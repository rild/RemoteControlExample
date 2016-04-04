package rimp.rild.com.android.remotecontrolexample;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.Toast;

/**
 * リモートコントロールからブロードキャストされるマルチメディアイベントを受け取るレシーバ。
 */
public class MusicPlayerReceiver extends BroadcastReceiver {
	private static final String TAG = "MusicPlayerReceiver";
	@Override
	public void onReceive(Context context, Intent intent) {
		if (intent.getAction().equals(android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY)) {
			Toast.makeText(context, "Headphones disconnected.", Toast.LENGTH_SHORT).show();
			// send an intent to our MusicPlayerService to telling it to pause the audio
			context.startService(new Intent(MusicPlayerService.ACTION_PAUSE));
		} else if (intent.getAction().equals(Intent.ACTION_MEDIA_BUTTON)) {
			KeyEvent keyEvent = (KeyEvent) intent.getExtras().get(Intent.EXTRA_KEY_EVENT);
			if (keyEvent.getAction() != KeyEvent.ACTION_DOWN) {
				return;
			}

			Log.d(TAG, "onReceive: action:" + (
					keyEvent.getAction() == KeyEvent.ACTION_DOWN ? "DOWN" :
					keyEvent.getAction() == KeyEvent.ACTION_UP ? "UP" :
					keyEvent.getAction() == KeyEvent.ACTION_MULTIPLE ? "MULTIPLE" : "Unknown")
					+ " keyCode:" + (
					keyEvent.getKeyCode() == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ? "MEDIA_PLAY_PAUSE" :
					keyEvent.getKeyCode() == KeyEvent.KEYCODE_MEDIA_STOP ? "MEDIA_STOP" :
					keyEvent.getKeyCode() == KeyEvent.KEYCODE_MEDIA_NEXT ? "MEDIA_NEXT" :
					keyEvent.getKeyCode() == KeyEvent.KEYCODE_MEDIA_PREVIOUS ? "KEYCODE_MEDIA_PREVIOUS" : "Unknown")
					+ " intent:" + intent);

			switch (keyEvent.getKeyCode()) {
			case KeyEvent.KEYCODE_HEADSETHOOK:
			case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
				context.startService(new Intent(MusicPlayerService.ACTION_PLAYPAUSE));
				break;
			case KeyEvent.KEYCODE_MEDIA_PLAY:
				context.startService(new Intent(MusicPlayerService.ACTION_PLAY));
				break;
			case KeyEvent.KEYCODE_MEDIA_PAUSE:
				context.startService(new Intent(MusicPlayerService.ACTION_PAUSE));
				break;
			case KeyEvent.KEYCODE_MEDIA_STOP:
				context.startService(new Intent(MusicPlayerService.ACTION_STOP));
				break;
			case KeyEvent.KEYCODE_MEDIA_NEXT:
				context.startService(new Intent(MusicPlayerService.ACTION_SKIP));
				break;
			case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
				// TODO: ensure that doing this in rapid succession actually
				// plays the previous song
				context.startService(new Intent(MusicPlayerService.ACTION_REWIND));
				break;
			}
		}
	}
}
