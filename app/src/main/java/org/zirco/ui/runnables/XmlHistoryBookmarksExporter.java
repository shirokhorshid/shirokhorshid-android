package org.zirco.ui.runnables;

import android.app.ProgressDialog;
import android.content.Context;
import android.database.Cursor;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.psiphon3.R;

import org.zirco.providers.ZircoBookmarksContentProvider;
import org.zirco.utils.ApplicationUtils;
import org.zirco.utils.IOUtils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Runnable to export history and bookmarks to an XML file.
 */
public class XmlHistoryBookmarksExporter implements Runnable {
	
	private Context mContext;
	private ProgressDialog mProgressDialog;
	private String mFileName;
	private Cursor mCursor;	
	
	private File mFile;
	private String mErrorMessage = null;
	
	/**
	 * Constructor.
	 * @param context The current context.
	 * @param fileName The output file.
	 * @param cursor The cursor to history and bookmarks.
	 * @param progressDialog The progress dialog shown during export.
	 */
	public XmlHistoryBookmarksExporter(Context context, String fileName, Cursor cursor, ProgressDialog progressDialog) {
		mContext = context;
		mFileName = fileName;
		mCursor = cursor;
		mProgressDialog = progressDialog;
	}

	@Override
	public void run() {
		try {
			
			mFile = new File(IOUtils.getBookmarksExportFolder(), mFileName);				
			FileWriter writer = new FileWriter(mFile);

			writer.write("<!DOCTYPE NETSCAPE-Bookmark-file-1>\n");
			writer.write("<!-- This is an automatically generated file.\n");
			writer.write("     It will be read and overwritten.\n");
			writer.write("     DO NOT EDIT! -->\n");
			writer.write("<META HTTP-EQUIV=\"Content-Type\" CONTENT=\"text/html; charset=UTF-8\">\n");
			writer.write("<TITLE>Bookmarks</TITLE>\n");
			writer.write("<H1>Bookmarks Menu</H1>\n");
			writer.write("\n");
			writer.write("<DL><p>\n");
			if (mCursor.moveToFirst()) {
				int titleIndex = mCursor.getColumnIndex(ZircoBookmarksContentProvider.BookmarkColumns.TITLE);
				int urlIndex = mCursor.getColumnIndex(ZircoBookmarksContentProvider.BookmarkColumns.URL);
				int dateIndex = mCursor.getColumnIndex(ZircoBookmarksContentProvider.BookmarkColumns.DATE);
				int createdIndex = mCursor.getColumnIndex(ZircoBookmarksContentProvider.BookmarkColumns.CREATED);
				int bookmarkIndex = mCursor.getColumnIndex(ZircoBookmarksContentProvider.BookmarkColumns.BOOKMARK);

				// Keep track if we have opened a new section such as Bookmarks or History
				boolean isOpenSection = false;
				int lastRecordType = -1;

				while (!mCursor.isAfterLast()) {
					// The sort order of the set is
					// BookmarkColumns.BOOKMARK  + " DESC, " +  BookmarkColumns.DATE + " DESC, " +  BookmarkColumns.CREATED + " DESC"
					// so we write out the bookmarks first followed by the history
					String url = mCursor.getString(urlIndex);
					String title = mCursor.getString(titleIndex);

					// Check if we need to close the section because we are switching from bookmarks to history
					int currentRecordType = mCursor.getInt(bookmarkIndex);
					if (lastRecordType != -1 && currentRecordType != lastRecordType) {
						isOpenSection = false;
						writer.write("</DL><p>\n");
					}

					lastRecordType = currentRecordType;

					if (!isOpenSection) {
						isOpenSection = true;
						if (lastRecordType >= 1) {
							writer.write("<DT><H3>Bookmarks</H3>\n");
						} else {
							writer.write("<DT><H3>History</H3>\n");
						}
						writer.write("<DL><p>");
					}

					// Write out the link
					writer.write("<DT>\n");
					writer.write(String.format("<DT><A HREF=\"%s\" ADD_DATE=\"%s\" LAST_VISIT=\"%s\">%s</A>\n",
							url != null ? url : "",
							mCursor.getLong(createdIndex),
							mCursor.getLong(dateIndex),
							title != null ? title : ""));
					writer.write("</DT>\n");

					mCursor.moveToNext();
				}
				// Check if the section is still open
				if (isOpenSection) {
					writer.write("</DL><p>\n");
				}
			}
			writer.write("</DL><p>\n");

			writer.flush();
			writer.close();
			
		} catch (IOException e1) {
			Log.w("Bookmark export failed", e1.toString());				
			mErrorMessage = e1.toString();
		}
		
		mHandler.sendEmptyMessage(0);
	}

    private Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            if (mProgressDialog != null) {
                mProgressDialog.dismiss();
            }

            if (mContext != null) {
                if (mErrorMessage == null) {
                    ApplicationUtils.showOkDialog(mContext,
                            android.R.drawable.ic_dialog_info,
                            mContext.getResources().getString(R.string.Commons_HistoryBookmarksExportSDCardDoneTitle),
                            String.format(mContext.getResources().getString(R.string.Commons_HistoryBookmarksExportSDCardDoneMessage), mFile.getAbsolutePath()));
                } else {
                    ApplicationUtils.showOkDialog(mContext,
                            android.R.drawable.ic_dialog_alert,
                            mContext.getResources().getString(R.string.Commons_HistoryBookmarksExportSDCardFailedTitle),
                            String.format(mContext.getResources().getString(R.string.Commons_HistoryBookmarksFailedMessage), mErrorMessage));
                }
            }
        }
    };
}
