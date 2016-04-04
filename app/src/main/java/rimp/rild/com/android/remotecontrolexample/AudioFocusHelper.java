package rimp.rild.com.android.remotecontrolexample;

import android.content.Context;
import android.media.AudioManager;

/**
 * AudioManager に対してオーディオフォーカスのリクエストを簡便にするためのヘルパークラス。
 * Android 2.2（API Level 8）から。
 */
public class AudioFocusHelper implements AudioManager.OnAudioFocusChangeListener {
	private AudioManager mAM;
	private MusicFocusable mFocusable;

	public AudioFocusHelper(Context context, MusicFocusable focusable) {
		mAM = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
		mFocusable = focusable;
	}

	/**
	 * オーディオフォーカスの取得を試みる。
	 * @return リクエストが成功したかどうか
	 */
	public boolean requestFocus() {
		return AudioManager.AUDIOFOCUS_REQUEST_GRANTED == mAM.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
	}

	/**
	 * オーディオフォーカスを手放すことを試みる。
	 * @return リクエストが成功したかどうか
	 */
	public boolean abandonFocus() {
		return AudioManager.AUDIOFOCUS_REQUEST_GRANTED == mAM.abandonAudioFocus(this);
	}

	@Override
	public void onAudioFocusChange(int focusChange) {
		switch (focusChange) {
		case AudioManager.AUDIOFOCUS_GAIN:
			mFocusable.onGainedAudioFocus();
			break;
		case AudioManager.AUDIOFOCUS_LOSS:
		case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
			mFocusable.onLostAudioFocus(false);
			break;
		case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
			mFocusable.onLostAudioFocus(true);
			break;
		}
	}
}
