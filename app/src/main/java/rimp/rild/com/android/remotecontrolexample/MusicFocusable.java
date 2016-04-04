package rimp.rild.com.android.remotecontrolexample;

/**
 * オーディオフォーカスイベントを簡素化するためのインターフェイス。
 * Android 2.2（API Level 8）以上で有効。
 */
public interface MusicFocusable {
	/** オーディオフォーカスを取得した際に呼び出される。 */
	public void onGainedAudioFocus();

	/**
	 * オーディオフォーカスを失った際に呼び出される。
	 * @param canDuck docked モード（低ボリューム）であるなら true、そうでないなら false
	 */
	public void onLostAudioFocus(boolean canDuck);
}
