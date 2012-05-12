package budo.budoist.views.adapters;

import java.util.Set;
import pl.polidea.treeview.AbstractTreeViewAdapter;
import budo.budoist.R;
import budo.budoist.models.Label;
import budo.budoist.services.TodoistClient;
import budo.budoist.services.TodoistOfflineStorage;
import budo.budoist.views.LabelListView;
import budo.budoist.views.LabelListView.LabelViewMode;
import pl.polidea.treeview.TreeNodeInfo;
import pl.polidea.treeview.TreeStateManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.text.Html;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.RelativeLayout;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Tree item adapter for a single label in the labels list 
 * 
 */
public class LabelTreeItemAdapter extends AbstractTreeViewAdapter<Label> {

	private LabelListView mLabelListView;
	private LabelViewMode mLabelViewMode;
	
    private Set<Integer> mSelected;
    
	private TodoistClient mClient;
	private TodoistOfflineStorage mStorage;
	
	private int mTextSize;
    
    private OnCheckedChangeListener onCheckedChange = new OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(final CompoundButton buttonView,
                final boolean isChecked) {
            final Label id = (Label)buttonView.getTag();
            changeSelected(isChecked, id);
            
	        TextView labelName = (TextView)
	        	((LinearLayout)buttonView.getParent()).findViewById(R.id.label_list_item_name);
            
            if (isChecked) {
            	labelName.setText(Html.fromHtml("<u><b>" + id.name + "</b></u>"));
            } else {
            	labelName.setText(Html.fromHtml("<u>" + id.name + "</u>"));
            }
        }
    };
    
    private void changeSelected(final boolean isChecked, final Label label) {
        if (isChecked) {
            mSelected.add(label.id);
        } else {
            mSelected.remove(label.id);
        }
    }
	
    public LabelTreeItemAdapter(final LabelListView labelListView,
    		final Set<Integer> selected,
            final TreeStateManager<Label> treeStateManager,
            final int numberOfLevels) {
        super(labelListView, treeStateManager, numberOfLevels);
        
        mSelected = selected;
        
        mLabelListView = labelListView;
        mLabelViewMode = mLabelListView.getViewMode();
        
        mClient = labelListView.getClient();
        mStorage = mClient.getStorage();
    }

    @Override
    public View getNewChildView(final TreeNodeInfo<Label> treeNodeInfo) {
        final LinearLayout viewLayout = (LinearLayout) getActivity()
                .getLayoutInflater().inflate(R.layout.label_list_item, null);
        return updateView(viewLayout, treeNodeInfo);
    }

    @Override
    public LinearLayout updateView(final View view,
            final TreeNodeInfo<Label> treeNodeInfo) {
        LinearLayout viewLayout = (LinearLayout) view;
        TextView itemCount = (TextView) viewLayout
            .findViewById(R.id.label_list_item_count);
        TextView labelName = (TextView) viewLayout
        	.findViewById(R.id.label_list_item_name);
        CheckBox checkbox = (CheckBox) viewLayout
        	.findViewById(R.id.label_list_item_checkbox);
        RelativeLayout labelCountLayout = (RelativeLayout) viewLayout
	    	.findViewById(R.id.label_list_item_count_layout);
        
        Label label = treeNodeInfo.getId();
        
        mTextSize = mStorage.getTextSize();
        
        itemCount.setText(String.valueOf((label.count)));
        itemCount.setTextSize(TypedValue.COMPLEX_UNIT_DIP, mTextSize);
        
        int alphaColor = 0xFF;
        if ((label.count == 0) && (mLabelViewMode == LabelViewMode.FILTER_BY_LABELS))
        	alphaColor = 0x50; // In case the label contains no items - make it a little blurred
        
        labelName.setText(Html.fromHtml("<u>" + label.name + "</u>"));
        labelName.setTextColor((alphaColor << 24) | label.getColor());
        labelName.setTextSize(TypedValue.COMPLEX_UNIT_DIP, mTextSize);
        
        if (mLabelViewMode == LabelViewMode.SELECT_LABELS) {
            // Set initial checkbox state (selected/unselected)
        	checkbox.setVisibility(View.VISIBLE);
        	checkbox.setTag(label);
        	checkbox.setChecked(mSelected.contains(label.id));
            checkbox.setOnCheckedChangeListener(onCheckedChange);
            labelCountLayout.setVisibility(View.GONE);
            
            if (mSelected.contains(label.id)) {
            	// Make it bold as well
            	labelName.setText(Html.fromHtml("<u><b>" + label.name + "</b></u>"));
            }
            
        } else {
        	checkbox.setVisibility(View.INVISIBLE);
        	
        	labelCountLayout.setVisibility(View.VISIBLE);
        }
        
        viewLayout.setTag(label);

        return viewLayout;
    }
    
    @Override
    public void handleItemClick(final View view, final Object id) {
    	
        if (mLabelViewMode == LabelViewMode.SELECT_LABELS) {
        	
        	// Check/Uncheck the checkbox
	        final ViewGroup vg = (ViewGroup) view;
	        final CheckBox cb = (CheckBox) vg
	                .findViewById(R.id.label_list_item_checkbox);
	        cb.performClick();
        }
    } 
    
    @Override
    public Drawable getBackgroundDrawable(final TreeNodeInfo<Label> treeNodeInfo) {
    	return new ColorDrawable(Color.WHITE);
    }

    @Override
    public long getItemId(final int position) {
        return getTreeId(position).id;
    }
}
