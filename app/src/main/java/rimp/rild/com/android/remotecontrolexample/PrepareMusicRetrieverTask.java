package rimp.rild.com.android.remotecontrolexample;

import java.util.List;

import android.content.Context;
import android.os.AsyncTask;

/**
 * 外部ストレージから音楽ファイルを探すための非同期タスク。
 */
public class PrepareMusicRetrieverTask extends AsyncTask<Context, Void, List<Item>> {
	private MusicRetrieverPreparedListener mListener;

	public PrepareMusicRetrieverTask(MusicRetrieverPreparedListener listener) {
		mListener = listener;
	}

	@Override
	protected List<Item> doInBackground(Context... arg) {
		return Item.getItems(arg[0]);
	}

	@Override
	protected void onPostExecute(List<Item> result) {
		mListener.onMusicRetrieverPrepared(result);
	}

	public interface MusicRetrieverPreparedListener {
		public void onMusicRetrieverPrepared(List<Item> items);
	}
}
