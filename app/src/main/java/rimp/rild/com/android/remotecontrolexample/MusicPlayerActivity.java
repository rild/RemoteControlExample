package rimp.rild.com.android.remotecontrolexample;

import java.io.IOException;
import java.util.List;

import android.app.Activity;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Chronometer;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

/**
 * MediaPlayer を直接使用する音楽プレイヤー。
 */
public class MusicPlayerActivity extends Activity implements View.OnClickListener,
		MediaPlayer.OnPreparedListener, MediaPlayer.OnInfoListener, MediaPlayer.OnCompletionListener {
	private static final String TAG = "MusicPlayerActivity";
	private MediaPlayer mMediaPlayer;
	private ImageButton mButtonPlayPause;
	private ImageButton mButtonSkip;
	private ImageButton mButtonRewind;
	private ImageButton mButtonStop;
	private TextView mTextViewArtist;
	private TextView mTextViewAlbum;
	private TextView mTextViewTitle;
	private Chronometer mChronometer;
	private Handler mHandler = new Handler();
	private List<Item> mItems;
	private int mIndex;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.music_player);

		mButtonPlayPause = (ImageButton) findViewById(R.id.playpause);
		mButtonSkip = (ImageButton) findViewById(R.id.skip);
		mButtonRewind = (ImageButton) findViewById(R.id.rewind);
		mButtonStop = (ImageButton) findViewById(R.id.stop);
		mTextViewArtist = (TextView) findViewById(R.id.artist);
		mTextViewAlbum = (TextView) findViewById(R.id.album);
		mTextViewTitle = (TextView) findViewById(R.id.title);
		mChronometer = (Chronometer) findViewById(R.id.chronometer);

		mButtonPlayPause.setOnClickListener(this);
		mButtonSkip.setOnClickListener(this);
		mButtonRewind.setOnClickListener(this);
		mButtonStop.setOnClickListener(this);

		setEnabledButton(false);
	}

	@Override
	protected void onResume() {
		super.onResume();
		Log.d(TAG, "onResume");
		mItems = Item.getItems(getApplicationContext());
		if (mItems.size() != 0) {
			mMediaPlayer = new MediaPlayer();
			mMediaPlayer.setOnPreparedListener(this);
			mMediaPlayer.setOnInfoListener(this);
			mMediaPlayer.setOnCompletionListener(this);
			prepare();
		}
	}

	@Override
	protected void onPause() {
		super.onPause();
		Log.d(TAG, "onPause");
		if (mMediaPlayer != null) {
			mMediaPlayer.reset();
			mMediaPlayer.release();
			mMediaPlayer = null;
			mChronometer.stop();
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		Log.d(TAG, "onDestroy");
	}

	@Override
	public void onClick(View v) {
		boolean isPlaying = mMediaPlayer.isPlaying();
		if (v == mButtonPlayPause) {
			if (isPlaying) {
				mMediaPlayer.pause();
				mChronometer.stop();
				mButtonPlayPause.setImageResource(R.drawable.media_play);
				mButtonPlayPause.setContentDescription(getResources().getText(R.string.play));
			} else {
				mMediaPlayer.start();
				mChronometer.setBase(SystemClock.elapsedRealtime() - mMediaPlayer.getCurrentPosition());
				mChronometer.start();
				mButtonPlayPause.setImageResource(R.drawable.media_pause);
				mButtonPlayPause.setContentDescription(getResources().getText(R.string.pause));
			}
		} else if (v == mButtonSkip) {
			mIndex = (mIndex + 1) % mItems.size();
			onClick(mButtonStop);
			if (isPlaying) {
				onClick(mButtonPlayPause);
			}
		} else if (v == mButtonRewind) {
			mMediaPlayer.seekTo(0);
			mChronometer.setBase(SystemClock.elapsedRealtime());
		} else if (v == mButtonStop) {
			mMediaPlayer.stop();
			mMediaPlayer.reset();
			mChronometer.stop();
			mChronometer.setBase(SystemClock.elapsedRealtime());
			prepare();
		}
	}

	private void prepare() {
		setEnabledButton(false);

		mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
		Item playingItem = mItems.get(mIndex);
		try {
			mMediaPlayer.setDataSource(getApplicationContext(), playingItem.getURI());
			mMediaPlayer.prepare();
		} catch (IllegalArgumentException e) {
			Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_LONG).show();
			e.printStackTrace();
		} catch (SecurityException e) {
			Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_LONG).show();
			e.printStackTrace();
		} catch (IllegalStateException e) {
			Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_LONG).show();
			e.printStackTrace();
		} catch (IOException e) {
			Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_LONG).show();
			e.printStackTrace();
		}
		mTextViewArtist.setText(playingItem.artist);
		mTextViewAlbum.setText(playingItem.album);
		mTextViewTitle.setText(playingItem.title);
		mButtonPlayPause.setImageResource(R.drawable.media_play);
		mButtonPlayPause.setContentDescription(getResources().getText(R.string.play));
		mChronometer.setBase(SystemClock.elapsedRealtime());
	}

	private void setEnabledButton(final boolean enabled) {
		Log.d(TAG, "setEnabledButton:" + enabled);
		mHandler.post(new Runnable() {
			@Override
			public void run() {
				mButtonPlayPause.setEnabled(enabled);
				mButtonSkip.setEnabled(enabled);
				mButtonRewind.setEnabled(enabled);
				mButtonStop.setEnabled(enabled);
			}
		});
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		switch (keyCode) {
		case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
		case KeyEvent.KEYCODE_HEADSETHOOK:
			onClick(mButtonPlayPause);
			return true;
		}
		return super.onKeyDown(keyCode, event);
	}

	@Override
	public void onPrepared(MediaPlayer mp) {
		Log.d(TAG, "onPrepared");
		setEnabledButton(true);
	}

	@Override
	public boolean onInfo(MediaPlayer mp, int what, int extra) {
		Log.d(TAG, "onInfo:" + (what == MediaPlayer.MEDIA_INFO_UNKNOWN ? "MEDIA_INFO_UNKNOWN" :
			what == MediaPlayer.MEDIA_INFO_VIDEO_TRACK_LAGGING ? "MEDIA_INFO_VIDEO_TRACK_LAGGING" :
			what == MediaPlayer.MEDIA_INFO_BUFFERING_START ? "MEDIA_INFO_BUFFERING_START" :
			what == MediaPlayer.MEDIA_INFO_BUFFERING_END ? "MEDIA_INFO_BUFFERING_END" :
			what == MediaPlayer.MEDIA_INFO_BAD_INTERLEAVING ? "MEDIA_INFO_BAD_INTERLEAVING" :
			what == MediaPlayer.MEDIA_INFO_NOT_SEEKABLE ? "MEDIA_INFO_NOT_SEEKABLE" :
			what == MediaPlayer.MEDIA_INFO_METADATA_UPDATE ? "MEDIA_INFO_METADATA_UPDATE" :
			"Unknown"));
		return false;
	}

	@Override
	public void onCompletion(MediaPlayer mp) {
		Log.d(TAG, "onCompletion");
		mHandler.post(new Runnable() {
			@Override
			public void run() {
				onClick(mButtonSkip);
				while (!mButtonPlayPause.isEnabled()) {
					try {
						Thread.sleep(100);
					} catch (InterruptedException e) {
					}
				}
				onClick(mButtonPlayPause);
			}
		});
	}
}
