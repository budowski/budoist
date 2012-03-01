package budo.budoist.views.adapters;

import pl.polidea.treeview.AbstractTreeViewAdapter;
import budo.budoist.R;
import budo.budoist.models.Project;
import budo.budoist.models.TodoistTextFormatter;
import budo.budoist.services.TodoistClient;
import budo.budoist.services.TodoistOfflineStorage;
import budo.budoist.views.ProjectListView;
import pl.polidea.treeview.TreeNodeInfo;
import pl.polidea.treeview.TreeStateManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.Log;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.RelativeLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Tree item adapter for a single project in the projects list 
 * 
 */
public class ProjectTreeItemAdapter extends AbstractTreeViewAdapter<Project> {
	
	private TodoistClient mClient;
	private TodoistOfflineStorage mStorage;
	
	private int mTextSize;

    public ProjectTreeItemAdapter(final ProjectListView projectListView,
            final TreeStateManager<Project> treeStateManager,
            final int numberOfLevels) {
        super(projectListView, treeStateManager, numberOfLevels);
        
        mClient = projectListView.getClient();
        mStorage = mClient.getStorage();
    }

    @Override
    public View getNewChildView(final TreeNodeInfo<Project> treeNodeInfo) {
        final LinearLayout viewLayout = (LinearLayout) getActivity()
                .getLayoutInflater().inflate(R.layout.project_list_item, null);
        return updateView(viewLayout, treeNodeInfo);
    }

    @Override
    public LinearLayout updateView(final View view,
            final TreeNodeInfo<Project> treeNodeInfo) {
        LinearLayout viewLayout = (LinearLayout) view;
        TextView itemCount = (TextView) viewLayout
                .findViewById(R.id.project_list_item_count);
        TextView projectName = (TextView) viewLayout
        	.findViewById(R.id.project_list_item_name);
        RelativeLayout itemCountLayout = (RelativeLayout) viewLayout
    		.findViewById(R.id.project_list_item_count_layout);
        GradientDrawable sh = (GradientDrawable) (itemCountLayout.getBackground());
        
        Project project = treeNodeInfo.getId();
        
        mTextSize = mStorage.getTextSize();
        
        // Set project name with formatting (highlighting, etc)
        projectName.setText(TodoistTextFormatter.formatText(project.getName()));
        projectName.setTextSize(TypedValue.COMPLEX_UNIT_DIP, mTextSize);
        itemCount.setText(String.valueOf((project.itemCount)));
        itemCount.setTextSize(TypedValue.COMPLEX_UNIT_DIP, mTextSize);
        
        int alphaColor = 0xFF;
        if (project.itemCount == 0)
        	alphaColor = 0x50; // In case the project contains no items - make it a little blurred
        
        sh.setColor((alphaColor << 24) | project.getColor());
        
        viewLayout.setTag(project);

        return viewLayout;
    }
    
    @Override
    public Drawable getBackgroundDrawable(final TreeNodeInfo<Project> treeNodeInfo) {
    	return new ColorDrawable(Color.WHITE);
    }

    @Override
    public long getItemId(final int position) {
        return getTreeId(position).id;
    }
}
