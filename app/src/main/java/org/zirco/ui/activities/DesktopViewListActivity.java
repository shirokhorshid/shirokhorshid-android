package org.zirco.ui.activities;

import com.psiphon3.R;
import com.psiphon3.psiphonlibrary.LocalizedActivities;
import org.zirco.controllers.Controller;
import org.zirco.model.DbAdapter;
import org.zirco.utils.ApplicationUtils;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.database.Cursor;
import android.os.Bundle;
import android.text.InputType;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.LayoutAnimationController;
import android.view.animation.TranslateAnimation;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.AdapterView.AdapterContextMenuInfo;

/**
 * Desktop View list activity.
 */
public class DesktopViewListActivity extends LocalizedActivities.ListActivity {
    
    private static final int MENU_ADD = Menu.FIRST;
    private static final int MENU_CLEAR = Menu.FIRST + 1;
    
    private static final int MENU_DELETE = Menu.FIRST + 10;
    
    private Cursor mCursor;
    private DbAdapter mDbAdapter;
    private SimpleCursorAdapter mCursorAdapter;
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.desktop_view_list_activity);
        
        setTitle(R.string.DesktopViewListActivity_Title);
        
        mDbAdapter = new DbAdapter(this);
        mDbAdapter.open();
        
        registerForContextMenu(getListView());
        
        fillData();
    }
    
    @Override
    protected void onDestroy() {
        mDbAdapter.close();
        mCursor.close();
        super.onDestroy();
    }
    
    /**
     * Fill the list view.
     */
    private void fillData() {
        mCursor = mDbAdapter.getDesktopViewUrlCursor();
        startManagingCursor(mCursor);
        
        String[] from = new String[] {DbAdapter.DESKTOP_VIEW_URL_URL};
        int[] to = new int[] {R.id.DesktopViewListRow_Title};
        
        mCursorAdapter = new SimpleCursorAdapter(this, R.layout.desktop_view_list_row, mCursor, from, to);
        setListAdapter(mCursorAdapter);
        
        setAnimation();
    }
    
    /**
     * Set the view loading animation.
     */
    private void setAnimation() {
        AnimationSet set = new AnimationSet(true);

        Animation animation = new AlphaAnimation(0.0f, 1.0f);
        animation.setDuration(100);
        set.addAnimation(animation);

        animation = new TranslateAnimation(
            Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF, 0.0f,
            Animation.RELATIVE_TO_SELF, -1.0f, Animation.RELATIVE_TO_SELF, 0.0f
        );
        animation.setDuration(100);
        set.addAnimation(animation);

        LayoutAnimationController controller =
                new LayoutAnimationController(set, 0.5f);
        ListView listView = getListView();        
        listView.setLayoutAnimation(controller);
    }
    
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        
        long id = ((AdapterContextMenuInfo) menuInfo).id;
        if (id != -1) {
            menu.setHeaderTitle(mDbAdapter.getDesktopViewUrlItemById(id));
        }       
        
        menu.add(0, MENU_DELETE, 0, R.string.Commons_Delete);
    }
    
    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
        
        switch (item.getItemId()) {
        case MENU_DELETE:
            mDbAdapter.deleteFromDesktopViewUrlList(info.id);
            Controller.getInstance().resetDesktopViewUrlList();
            fillData();
            return true;
        default: return super.onContextItemSelected(item);
        }
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        
        MenuItem item;
        item = menu.add(0, MENU_ADD, 0, R.string.Commons_Add);
        item.setIcon(R.drawable.ic_menu_add);
        
        item = menu.add(0, MENU_CLEAR, 0, R.string.Commons_Clear);
        item.setIcon(R.drawable.ic_menu_delete);
        
        return true;
    }
    
    @Override
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
        
        switch(item.getItemId()) {
        case MENU_ADD:
            addToDesktopViewList();
            return true;
            
        case MENU_CLEAR:            
            clearDesktopViewList();
            return true;
        default: return super.onMenuItemSelected(featureId, item);
        }
    }
    
    /**
     * Add a value to the list.
     * @param value The value to add.
     */
    private void doAddToDesktopViewList(String value) {
        mDbAdapter.insertInDesktopViewUrlList(value);
        Controller.getInstance().resetMobileViewUrlList();
        fillData();
    }
    
    private void addToDesktopViewList() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);    
        builder.setCancelable(true);
        builder.setIcon(android.R.drawable.ic_input_add);
        builder.setTitle(getResources().getString(R.string.DesktopViewListActivity_AddMessage));
        
        builder.setInverseBackgroundForced(true);
        
        // Set an EditText view to get user input 
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        builder.setView(input);
        
        builder.setInverseBackgroundForced(true);
        builder.setPositiveButton(getResources().getString(R.string.Commons_Ok), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                doAddToDesktopViewList(input.getText().toString());
            }
        });
        builder.setNegativeButton(getResources().getString(R.string.Commons_Cancel), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        AlertDialog alert = builder.create();
        alert.show();

    }
    
    /**
     * Clear the list.
     */
    private void doClearDesktopViewList() {
        mDbAdapter.clearDesktopViewUrlList();
        Controller.getInstance().resetDesktopViewUrlList();
        fillData();
    }
    
    /**
     * Display a confirmation dialog and clear the list.
     */
    private void clearDesktopViewList() {
        ApplicationUtils.showYesNoDialog(this,
                android.R.drawable.ic_dialog_alert,
                R.string.DesktopViewListActivity_ClearMessage,
                R.string.Commons_NoUndoMessage,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        doClearDesktopViewList();
                    }           
        });      
    }

}
