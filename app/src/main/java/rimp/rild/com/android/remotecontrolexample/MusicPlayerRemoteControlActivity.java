package rimp.rild.com.android.remotecontrolexample;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Chronometer;
import android.widget.ImageButton;
import android.widget.TextView;

/**
 * サービス経由で音楽をコントロールするプレイヤー。
 * このプレイヤーは、ロック画面と Notification で状態の同期が取られる。
 */
public class MusicPlayerRemoteControlActivity extends Activity implements OnClickListener {
	private static final String TAG = "MusicPlayerRemoteControlActivity";
	private ImageButton mButtonPlayPause;
	private ImageButton mButtonSkip;
	private ImageButton mButtonRewind;
	private ImageButton mButtonStop;
	private TextView mTextViewArtist;
	private TextView mTextViewAlbum;
	private TextView mTextViewTitle;
	private Chronometer mChronometer;
	private Handler mHandler = new Handler();
	private long mCurrentPosition;
	private IntentFilter mFilter;
	private BroadcastReceiver mReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, final Intent intent) {
			mHandler.post(new Runnable() {
				public void run() {
					mTextViewArtist.setText(intent.getStringExtra("artist"));
					mTextViewAlbum.setText(intent.getStringExtra("album"));
					mTextViewTitle.setText(intent.getStringExtra("title"));
					String state = intent.getStringExtra("state");
					mCurrentPosition = intent.getIntExtra("currentPosition", 0);
					mChronometer.setBase(SystemClock.elapsedRealtime() - mCurrentPosition);
					if (state.equals(MusicPlayerService.State.Playing.toString())) {
						playing();
					} else if (state.equals(MusicPlayerService.State.Paused.toString())) {
						paused();
					} else if (state.equals(MusicPlayerService.State.Stopped.toString())) {
						stopped();
					}
				}
			});
		}
	};

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

		mFilter = new IntentFilter();
		mFilter.addAction(MusicPlayerService.ACTION_STATE_CHANGED);
	}

	@Override
	protected void onResume() {
		super.onResume();
		Log.d(TAG, "onResume");
		registerReceiver(mReceiver, mFilter);
		startService(new Intent(MusicPlayerService.ACTION_REQUEST_STATE));
	}

	@Override
	protected void onPause() {
		super.onPause();
		Log.d(TAG, "onPause");
		unregisterReceiver(mReceiver);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		Log.d(TAG, "onDestroy");
	}

	@Override
	public void onClick(View v) {
		if (v == mButtonPlayPause) {
			if (mButtonPlayPause.getContentDescription().equals(getResources().getText(R.string.play))) {
				startService(new Intent(MusicPlayerService.ACTION_PLAY));
				playing();
			} else {
				startService(new Intent(MusicPlayerService.ACTION_PAUSE));
				paused();
			}
		} else if (v == mButtonSkip) {
			startService(new Intent(MusicPlayerService.ACTION_SKIP));
			mChronometer.stop();
			mChronometer.setBase(SystemClock.elapsedRealtime());
		} else if (v == mButtonRewind) {
			startService(new Intent(MusicPlayerService.ACTION_REWIND));
			mChronometer.setBase(SystemClock.elapsedRealtime());
		} else if (v == mButtonStop) {
			Intent intent = new Intent(MusicPlayerService.ACTION_STOP);
			intent.putExtra("cancel", true);
			startService(intent);
			stopped();
		}
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

	private void paused() {
		mCurrentPosition = SystemClock.elapsedRealtime() - mChronometer.getBase();
		mChronometer.stop();
		mButtonPlayPause.setImageResource(R.drawable.media_play);
		mButtonPlayPause.setContentDescription(getResources().getText(R.string.play));
	}

	private void playing() {
		mChronometer.setBase(SystemClock.elapsedRealtime() - mCurrentPosition);
		mChronometer.start();
		mButtonPlayPause.setImageResource(R.drawable.media_pause);
		mButtonPlayPause.setContentDescription(getResources().getText(R.string.pause));
	}

	private void stopped() {
		mChronometer.stop();
		mButtonPlayPause.setImageResource(R.drawable.media_play);
		mButtonPlayPause.setContentDescription(getResources().getText(R.string.play));
		mChronometer.setBase(SystemClock.elapsedRealtime());
	}
}
