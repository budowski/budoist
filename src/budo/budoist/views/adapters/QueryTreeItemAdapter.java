package budo.budoist.views.adapters;

import pl.polidea.treeview.AbstractTreeViewAdapter;
import budo.budoist.R;
import budo.budoist.models.Query;
import budo.budoist.services.TodoistClient;
import budo.budoist.services.TodoistOfflineStorage;
import budo.budoist.views.QueryListView;
import pl.polidea.treeview.TreeNodeInfo;
import pl.polidea.treeview.TreeStateManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;

/**
 * Tree item adapter for a single query in the queries list 
 * 
 */
public class QueryTreeItemAdapter extends AbstractTreeViewAdapter<Query> {
	private QueryListView mQueryListView;
	
	private TodoistClient mClient;
	private TodoistOfflineStorage mStorage;
	
	private int mTextSize;
	
    public QueryTreeItemAdapter(final QueryListView queryListView,
            final TreeStateManager<Query> treeStateManager,
            final int numberOfLevels) {
        super(queryListView, treeStateManager, numberOfLevels);
        
        mQueryListView = queryListView;
        
        mClient = queryListView.getClient();
        mStorage = mClient.getStorage();
    }

    @Override
    public View getNewChildView(final TreeNodeInfo<Query> treeNodeInfo) {
        final RelativeLayout viewLayout = (RelativeLayout) getActivity()
                .getLayoutInflater().inflate(R.layout.query_list_item, null);
        return updateView(viewLayout, treeNodeInfo);
    }

    @Override
    public RelativeLayout updateView(final View view,
            final TreeNodeInfo<Query> treeNodeInfo) {
        RelativeLayout viewLayout = (RelativeLayout) view;
        TextView queryName = (TextView) viewLayout
        	.findViewById(R.id.query_list_item_name);
        
        mTextSize = mStorage.getTextSize();
        
        Query query = treeNodeInfo.getId();
        
        queryName.setText(query.name);
        queryName.setTextSize(TypedValue.COMPLEX_UNIT_DIP, mTextSize);
 
        viewLayout.setTag(query);

        return viewLayout;
    }
    
   
    @Override
    public Drawable getBackgroundDrawable(final TreeNodeInfo<Query> treeNodeInfo) {
    	return new ColorDrawable(Color.WHITE);
    }

    @Override
    public long getItemId(final int position) {
        return getTreeId(position).id;
    }

}
