package rimp.rild.com.android.remotecontrolexample;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.media.RemoteControlClient;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.Toast;

import java.io.IOException;
import java.util.List;

/**
 * MediaPlayer を内包した音楽プレイヤーサービス。
 * Activity、ロック画面、Notification の音楽コントローラからの操作を受け付け、それらの状態を同期する。
 */
public class MusicPlayerService extends Service implements OnCompletionListener,
		OnPreparedListener, OnErrorListener, MusicFocusable,
		PrepareMusicRetrieverTask.MusicRetrieverPreparedListener {
	final static String TAG = "MusicService";

	public static final String ACTION_STATE_CHANGED = "com.example.android.remotecontrol.ACTION_STATE_CHANGED";
	public static final String ACTION_PLAYPAUSE = "com.example.android.remotecontrol.ACTION_PLAYPAUSE";
	public static final String ACTION_PLAY = "com.example.android.remotecontrol.ACTION_PLAY";
	public static final String ACTION_PAUSE = "com.example.android.remotecontrol.ACTION_PAUSE";
	public static final String ACTION_SKIP = "com.example.android.remotecontrol.ACTION_SKIP";
	public static final String ACTION_REWIND = "com.example.android.remotecontrol.ACTION_REWIND";
	public static final String ACTION_STOP = "com.example.android.remotecontrol.ACTION_STOP";
	public static final String ACTION_REQUEST_STATE = "com.example.android.remotecontrol.ACTION_REQUEST_STATE";

	// The volume we set the media player to when we lose audio focus, but are
	// allowed to reduce the volume instead of stopping playback.
	public static final float DUCK_VOLUME = 0.1f;

	// our media player
	private MediaPlayer mPlayer = null;

	// our AudioFocusHelper object, if it's available (it's available on SDK level >= 8)
	// If not available, this will be null. Always check for null before using!
	private AudioFocusHelper mAudioFocusHelper = null;

	// indicates the state our service:
	enum State {
		Retrieving,	// the MediaRetriever is retrieving music
		Stopped,	// media player is stopped and not prepared to play
		Preparing,	// media player is preparing...
		Playing, 	// playback active (media player ready!). (but the media player
					// may actually be paused in this state if we don't have audio focus.
					// But we stay in this state so that we know we have to resume playback once we get focus back)
		Paused		// playback paused (media player ready!)
	};

	private State mState = State.Retrieving;

	// if in Retrieving mode, this flag indicates whether we should start
	// playing immediately when we are ready or not.
	boolean mStartPlayingAfterRetrieve = false;

	// do we have audio focus?
	enum AudioFocus {
		NoFocusNoDuck,	// we don't have audio focus, and can't duck
		NoFocusCanDuck,	// we don't have focus, but can play at a low volume ("ducking")
		Focused			// we have full audio focus
	}

	private AudioFocus mAudioFocus = AudioFocus.NoFocusNoDuck;

	private List<Item> mItems;
	private int mIndex;

	// The ID we use for the notification (the onscreen alert that appears at the notification
	// area at the top of the screen as an icon -- and as text as well if the user expands the
	// notification area).
	final int NOTIFICATION_ID = 1;

	// our RemoteControlClient object, which will use remote control APIs available in
	// SDK level >= 14, if they're available.
	private RemoteControlClient mRemoteControlClient;

	// Dummy album art we will pass to the remote control (if the APIs are available).
	private Bitmap mDummyAlbumArt;

	// The component name of MusicIntentReceiver, for use with media button and remote control APIs
	private ComponentName mMediaButtonReceiverComponent;

	private AudioManager mAudioManager;
	private NotificationManager mNotificationManager;

	private Notification mNotification = null;

	private Boolean mIsOnlyPrepare;

	private long mRelaxTime = System.currentTimeMillis();

	private Thread mSelfStopThread = new Thread() {
		public void run() {
			while (true) {
				// 停止後 10 分再生がなかったらサービスを止める
				boolean needSleep = false;
				if (mState == MusicPlayerService.State.Preparing || mState == MusicPlayerService.State.Playing || mState == MusicPlayerService.State.Paused) {
					needSleep = true;
				} else if (mRelaxTime + 10 * 1000 * 60 > System.currentTimeMillis()) {
					needSleep = true;
				}
				if (!needSleep) {
					break;
				}
				try {
					Thread.sleep(1 * 1000 * 60); // 停止中でない、または 10 分経過してない場合は 1 分休む
				} catch (InterruptedException e) {
				}
			}
			MusicPlayerService.this.stopSelf();
		}
	};

	/**
	 * Makes sure the media player exists and has been reset. This will create
	 * the media player if needed, or reset the existing media player if one
	 * already exists.
	 */
	private void createMediaPlayerIfNeeded() {
		if (mPlayer == null) {
			mPlayer = new MediaPlayer();

			// Make sure the media player will acquire a wake-lock while
			// playing. If we don't do
			// that, the CPU might go to sleep while the song is playing,
			// causing playback to stop.
			//
			// Remember that to use this, we have to declare the
			// android.permission.WAKE_LOCK
			// permission in AndroidManifest.xml.
			mPlayer.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);

			// we want the media player to notify us when it's ready preparing,
			// and when it's done
			// playing:
			mPlayer.setOnPreparedListener(this);
			mPlayer.setOnCompletionListener(this);
			mPlayer.setOnErrorListener(this);
		} else {
			mPlayer.reset();
		}
	}

	@Override
	public void onCreate() {
		Log.d(TAG, "onCreate");

		mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		mAudioManager = (AudioManager) getSystemService(AUDIO_SERVICE);

		// Create the retriever and start an asynchronous task that will prepare it.
		new PrepareMusicRetrieverTask(this).execute(getApplicationContext());

		// create the Audio Focus Helper, if the Audio Focus feature is
		// available (SDK 8 or above)
		if (android.os.Build.VERSION.SDK_INT >= 8) {
			mAudioFocusHelper = new AudioFocusHelper(getApplicationContext(), this);
		} else {
			mAudioFocus = AudioFocus.Focused; // no focus feature, so we always "have" audio focus
		}

		mDummyAlbumArt = BitmapFactory.decodeResource(getResources(), R.drawable.dummy_album_art);

		mMediaButtonReceiverComponent = new ComponentName(this, MusicPlayerReceiver.class);

		Intent intent;

		intent = new Intent(this, MusicPlayerRemoteControlActivity.class);
		PendingIntent pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
		RemoteViews views = new RemoteViews(getPackageName(), R.layout.remote_control);
		mNotification = new Notification();
		mNotification.icon = R.drawable.playing;
		mNotification.contentView = views;
		mNotification.flags = Notification.FLAG_ONGOING_EVENT | Notification.FLAG_NO_CLEAR;
		mNotification.contentIntent = pi;

		intent = new Intent(ACTION_REWIND);
		PendingIntent piRewind = PendingIntent.getService(this, R.id.rewind, intent, PendingIntent.FLAG_UPDATE_CURRENT);
		views.setOnClickPendingIntent(R.id.rewind, piRewind);

		intent = new Intent(ACTION_PLAYPAUSE);
		PendingIntent piPlayPause = PendingIntent.getService(this, R.id.playpause, intent, PendingIntent.FLAG_UPDATE_CURRENT);
		views.setOnClickPendingIntent(R.id.playpause, piPlayPause);

		intent = new Intent(ACTION_SKIP);
		PendingIntent piSkip = PendingIntent.getService(this, R.id.skip, intent, PendingIntent.FLAG_UPDATE_CURRENT);
		views.setOnClickPendingIntent(R.id.skip, piSkip);

		intent = new Intent(ACTION_STOP);
		PendingIntent piStop = PendingIntent.getService(this, R.id.stop, intent, PendingIntent.FLAG_UPDATE_CURRENT);
		views.setOnClickPendingIntent(R.id.stop, piStop);

		mSelfStopThread.start();
	}

	/**
	 * Called when we receive an Intent. When we receive an intent sent to us
	 * via startService(), this is the method that gets called. So here we react
	 * appropriately depending on the Intent's action, which specifies what is
	 * being requested of us.
	 */
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		String action = intent.getAction();
		if (action.equals(ACTION_PLAYPAUSE)) {
			processTogglePlaybackRequest();
		} else if (action.equals(ACTION_PLAY)) {
			processPlayRequest();
		} else if (action.equals(ACTION_PAUSE)) {
			processPauseRequest();
		} else if (action.equals(ACTION_SKIP)) {
			processSkipRequest();
		} else if (action.equals(ACTION_STOP)) {
			processStopRequest();
			if (intent.getBooleanExtra("cancel", false)) {
				mNotificationManager.cancel(NOTIFICATION_ID);
			}
		} else if (action.equals(ACTION_REWIND)) {
			processRewindRequest();
		} else if (action.equals(ACTION_REQUEST_STATE)) {
			sendPlayerState();
		}

		return START_NOT_STICKY;	// Means we started the service, but don't want it to
									// restart in case it's killed.
	}

	private void processTogglePlaybackRequest() {
		if (mState == State.Paused || mState == State.Stopped) {
			processPlayRequest();
		} else {
			processPauseRequest();
		}
	}

	private void processPlayRequest() {
		Log.d(TAG, "processPlayRequest:" + mState);
		if (mState == State.Retrieving) {
			// If we are still retrieving media, just set the flag to start
			// playing when we're
			// ready
			mStartPlayingAfterRetrieve = true;
			return;
		}

		tryToGetAudioFocus();

		// actually play the song

		if (mState == State.Stopped) {
			// If we're stopped, just go ahead to the next song and start
			// playing
			playNextSong(false);
		} else if (mState == State.Paused) {
			// If we're paused, just continue playback and restore the
			// 'foreground service' state.
			mState = State.Playing;
			configAndStartMediaPlayer();
		}

		// Tell any remote controls that our playback state is 'playing'.
		if (mRemoteControlClient != null) {
			mRemoteControlClient.setPlaybackState(RemoteControlClient.PLAYSTATE_PLAYING);
		}
	}

	private void processPauseRequest() {
		Log.d(TAG, "processPauseRequest:" + mState);
		if (mState == State.Retrieving) {
			// If we are still retrieving media, clear the flag that indicates
			// we should start
			// playing when we're ready
			mStartPlayingAfterRetrieve = false;
			return;
		}

		if (mState == State.Playing) {
			// Pause media player and cancel the 'foreground service' state.
			mState = State.Paused;
			mPlayer.pause();
			relaxResources(false); // while paused, we always retain the MediaPlayer do not give up audio focus
			updateNotification();
			sendPlayerState();
		}

		// Tell any remote controls that our playback state is 'paused'.
		if (mRemoteControlClient != null) {
			mRemoteControlClient.setPlaybackState(RemoteControlClient.PLAYSTATE_PAUSED);
		}
	}

	private void processRewindRequest() {
		if (mState == State.Playing || mState == State.Paused) {
			mPlayer.seekTo(0);
			updateNotification();
			sendPlayerState();
		}
	}

	private void processSkipRequest() {
		if (mState == State.Playing || mState == State.Paused) {
			tryToGetAudioFocus();
			mIndex = (mIndex + 1) % mItems.size();

			playNextSong(false);
		} else if (mState == State.Stopped) {
			mIndex = (mIndex + 1) % mItems.size();
			playNextSong(true);
		}
	}

	private void processStopRequest() {
		processStopRequest(false);
	}

	private void processStopRequest(boolean force) {
		if (mState == State.Playing || mState == State.Paused || force) {
			mState = State.Stopped;

			// let go of all resources...
			relaxResources(true);
			giveUpAudioFocus();

			// Tell any remote controls that our playback state is 'paused'.
			if (mRemoteControlClient != null) {
				mRemoteControlClient.setPlaybackState(RemoteControlClient.PLAYSTATE_STOPPED);
			}

			updateNotification();
			sendPlayerState();
		}
	}

	/**
	 * Releases resources used by the service for playback. This includes the
	 * "foreground service" status and notification, the wake locks and possibly
	 * the MediaPlayer.
	 * 
	 * @param releaseMediaPlayer Indicates whether the Media Player should also be released or not
	 */
	private void relaxResources(boolean releaseMediaPlayer) {
		// stop being a foreground service
		stopForeground(true);

		// stop and release the Media Player, if it's available
		if (releaseMediaPlayer && mPlayer != null) {
			mPlayer.reset();
			mPlayer.release();
			mPlayer = null;
		}
		mRelaxTime = System.currentTimeMillis();
	}

	private void giveUpAudioFocus() {
		if (mAudioFocus == AudioFocus.Focused && mAudioFocusHelper != null && mAudioFocusHelper.abandonFocus()) {
			mAudioFocus = AudioFocus.NoFocusNoDuck;
		}
	}

	/**
	 * Reconfigures MediaPlayer according to audio focus settings and
	 * starts/restarts it. This method starts/restarts the MediaPlayer
	 * respecting the current audio focus state. So if we have focus, it will
	 * play normally; if we don't have focus, it will either leave the
	 * MediaPlayer paused or set it to a low volume, depending on what is
	 * allowed by the current focus settings. This method assumes mPlayer !=
	 * null, so if you are calling it, you have to do so from a context where
	 * you are sure this is the case.
	 */
	private void configAndStartMediaPlayer() {
		if (mAudioFocus == AudioFocus.NoFocusNoDuck) {
			// If we don't have audio focus and can't duck, we have to pause,
			// even if mState
			// is State.Playing. But we stay in the Playing state so that we
			// know we have to resume
			// playback once we get the focus back.
			if (mPlayer.isPlaying()) {
				mPlayer.pause();
				mState = State.Paused;
				updateNotification();
				sendPlayerState();
			}
			return;
		} else if (mAudioFocus == AudioFocus.NoFocusCanDuck) {
			mPlayer.setVolume(DUCK_VOLUME, DUCK_VOLUME); // we'll be relatively quiet
		} else {
			mPlayer.setVolume(1.0f, 1.0f); // we can be loud
		}

		if (!mPlayer.isPlaying()) {
			mPlayer.start();
			updateNotification();
			sendPlayerState();
		}
	}

	private void tryToGetAudioFocus() {
		if (mAudioFocus != AudioFocus.Focused && mAudioFocusHelper != null && mAudioFocusHelper.requestFocus()) {
			mAudioFocus = AudioFocus.Focused;
		}
	}

	/**
	 * Starts playing the next song. If manualUrl is null, the next song will be
	 * randomly selected from our Media Retriever (that is, it will be a random
	 * song in the user's device). If manualUrl is non-null, then it specifies
	 * the URL or path to the song that will be played next.
	 */
	private void playNextSong(boolean isOnlyPrepare) {
		mIsOnlyPrepare = isOnlyPrepare;
		mState = State.Stopped;
		relaxResources(false); // release everything except MediaPlayer

		try {
			Item playingItem = mItems.get(mIndex);
			if (playingItem == null) {
				Toast.makeText(this, "No available music to play. Place some music on your external storage device (e.g. your SD card) and try again.", Toast.LENGTH_LONG).show();
				processStopRequest(true); // stop everything!
				return;
			}

			// set the source of the media player a a content URI
			createMediaPlayerIfNeeded();
			mPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
			mPlayer.setDataSource(getApplicationContext(), playingItem.getURI());

			mState = State.Preparing;

			// Use the media button APIs (if available) to register ourselves
			// for media button events
			mAudioManager.registerMediaButtonEventReceiver(mMediaButtonReceiverComponent);

			// Use the remote control APIs (if available) to set the playback state

			if (mRemoteControlClient == null) {
				Intent intent = new Intent(Intent.ACTION_MEDIA_BUTTON);
				intent.setComponent(mMediaButtonReceiverComponent);
				mRemoteControlClient = new RemoteControlClient(PendingIntent.getBroadcast(this, 0 , intent, 0));
				mAudioManager.registerRemoteControlClient(mRemoteControlClient);
			}

			mRemoteControlClient.setPlaybackState(RemoteControlClient.PLAYSTATE_PLAYING);

			mRemoteControlClient.setTransportControlFlags(RemoteControlClient.FLAG_KEY_MEDIA_PLAY
							| RemoteControlClient.FLAG_KEY_MEDIA_PAUSE
							| RemoteControlClient.FLAG_KEY_MEDIA_NEXT
							| RemoteControlClient.FLAG_KEY_MEDIA_STOP);

			// Update the remote controls
			mRemoteControlClient.editMetadata(true)
					.putString(MediaMetadataRetriever.METADATA_KEY_ARTIST, playingItem.artist)
					.putString(MediaMetadataRetriever.METADATA_KEY_ALBUM, playingItem.album)
					.putString(MediaMetadataRetriever.METADATA_KEY_TITLE, playingItem.title)
					.putLong(MediaMetadataRetriever.METADATA_KEY_DURATION, playingItem.duration)
					// TODO: fetch real item artwork
					.putBitmap(RemoteControlClient.MetadataEditor.BITMAP_KEY_ARTWORK, mDummyAlbumArt).apply();

			// starts preparing the media player in the background.
			// When it's done, it will call our OnPreparedListener
			// (that is, the onPrepared() method on this class, since we set the listener to 'this').
			// Until the media player is prepared, we *cannot* call start() on it!
			mPlayer.prepareAsync();
		} catch (IOException e) {
			Log.e("MusicService", "IOException playing next song: " + e.getMessage());
			e.printStackTrace();
		}
	}

	/** Called when media player is done playing current song. */
	public void onCompletion(MediaPlayer player) {
		// The media player finished playing the current song, so we go ahead
		// and start the next.
		mIndex = (mIndex + 1) % mItems.size();
		playNextSong(false);
	}

	/** Called when media player is done preparing. */
	public void onPrepared(MediaPlayer player) {
		// The media player is done preparing. That means we can start playing!
		if (mIsOnlyPrepare) {
			mState = State.Stopped;
		} else {
			mState = State.Playing;
		}
		updateNotification();
		sendPlayerState();
		if (!mIsOnlyPrepare) {
			configAndStartMediaPlayer();
		}
	}

	/** Updates the notification. */
	private void updateNotification() {
		boolean playing = mState == State.Playing;

		mNotification.icon = playing ? R.drawable.playing : R.drawable.pausing;
		int playPauseRes = playing ? R.drawable.media_pause_s : R.drawable.media_play_s;
		mNotification.contentView.setImageViewResource(R.id.playpause, playPauseRes);

		Item playingItem = mItems.get(mIndex);
		mNotification.contentView.setTextViewText(R.id.artist, playingItem.artist);
		mNotification.contentView.setTextViewText(R.id.album, playingItem.album);
		mNotification.contentView.setTextViewText(R.id.title, playingItem.title);
		long current;
		if (mState == State.Stopped) {
			current = 0;
		} else {
			current = mPlayer.getCurrentPosition();
		}
		mNotification.contentView.setChronometer(R.id.chronometer, SystemClock.elapsedRealtime() - current, null, playing);

		mNotificationManager.notify(NOTIFICATION_ID, mNotification);
	}

	/**
	 * Called when there's an error playing media. When this happens, the media
	 * player goes to the Error state. We warn the user about the error and
	 * reset the media player.
	 */
	public boolean onError(MediaPlayer mp, int what, int extra) {
		Toast.makeText(getApplicationContext(), "Media player error! Resetting.", Toast.LENGTH_SHORT).show();
		Log.e(TAG, "Error: what=" + String.valueOf(what) + ", extra=" + String.valueOf(extra));

		mState = State.Stopped;
		relaxResources(true);
		giveUpAudioFocus();
		return true; // true indicates we handled the error
	}

	public void onGainedAudioFocus() {
		Toast.makeText(getApplicationContext(), "gained audio focus.", Toast.LENGTH_SHORT).show();
		mAudioFocus = AudioFocus.Focused;

		// restart media player with new focus settings
		if (mState == State.Playing) {
			configAndStartMediaPlayer();
		}
	}

	public void onLostAudioFocus(boolean canDuck) {
		Toast.makeText(getApplicationContext(), "lost audio focus." + (canDuck ? "can duck" : "no duck"), Toast.LENGTH_SHORT).show();
		mAudioFocus = canDuck ? AudioFocus.NoFocusCanDuck : AudioFocus.NoFocusNoDuck;

		// start/restart/pause media player with new focus settings
		if (mPlayer != null && mPlayer.isPlaying()) {
			configAndStartMediaPlayer();
		}
	}

	private void sendPlayerState() {
		if (mItems != null) {
			Item playingItem = mItems.get(mIndex);
			Intent intent = new Intent(ACTION_STATE_CHANGED);
			intent.putExtra("artist", playingItem.artist);
			intent.putExtra("album", playingItem.album);
			intent.putExtra("title", playingItem.title);
			intent.putExtra("state", mState.toString());
			if (mPlayer != null) {
				intent.putExtra("currentPosition", mPlayer.getCurrentPosition());
			}
			sendBroadcast(intent);
		}
	}

	@Override
	public void onMusicRetrieverPrepared(List<Item> items) {
		// Done retrieving!
		mState = State.Stopped;
		mItems = items;
		sendPlayerState();

		// If the flag indicates we should start playing after retrieving, let's
		// do that now.
		if (mStartPlayingAfterRetrieve) {
			tryToGetAudioFocus();
			playNextSong(false);
		}
	}

	@Override
	public void onDestroy() {
		Log.d(TAG, "onDestroy");
		// Service is being killed, so make sure we release our resources
		mState = State.Stopped;
		relaxResources(true);
		giveUpAudioFocus();
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
}
