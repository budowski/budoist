package budo.budoist.views.adapters;

import pl.polidea.treeview.AbstractTreeViewAdapter;
import budo.budoist.R;
import budo.budoist.models.Note;
import budo.budoist.models.TodoistTextFormatter;
import budo.budoist.services.TodoistClient;
import budo.budoist.services.TodoistOfflineStorage;
import budo.budoist.views.NoteListView;
import pl.polidea.treeview.TreeNodeInfo;
import pl.polidea.treeview.TreeStateManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.text.util.Linkify;
import android.util.TypedValue;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

/**
 * Tree item adapter for a single note in the notes list 
 * 
 */
public class NoteTreeItemAdapter extends AbstractTreeViewAdapter<Note> implements OnClickListener {

	private NoteListView mNoteListView;
	
	public interface IOnNoteDelete { void onNoteDelete(Note note); }
	public interface IOnNoteEdit { void onNoteEdit(Note note); }
	
	private IOnNoteDelete mOnNoteDelete = null;
	private IOnNoteEdit mOnNoteEdit = null;
	
	private ImageView mNoteDelete;
	private ImageView mNoteEdit;

	private TodoistClient mClient;
	private TodoistOfflineStorage mStorage;
	
	private int mTextSize;
	
    public NoteTreeItemAdapter(final NoteListView noteListView,
    		final IOnNoteDelete onNoteDelete,
    		final IOnNoteEdit onNoteEdit,
            final TreeStateManager<Note> treeStateManager,
            final int numberOfLevels) {
        super(noteListView, treeStateManager, numberOfLevels);
        
        mNoteListView = noteListView;
        mOnNoteDelete = onNoteDelete;
        mOnNoteEdit = onNoteEdit;
        
        mClient = noteListView.getClient();
        mStorage = mClient.getStorage();
    }

    @Override
    public View getNewChildView(final TreeNodeInfo<Note> treeNodeInfo) {
        final RelativeLayout viewLayout = (RelativeLayout) getActivity()
                .getLayoutInflater().inflate(R.layout.note_list_item, null);
        return updateView(viewLayout, treeNodeInfo);
    }

    @Override
    public RelativeLayout updateView(final View view,
            final TreeNodeInfo<Note> treeNodeInfo) {
        RelativeLayout viewLayout = (RelativeLayout) view;
        TextView noteContent = (TextView) viewLayout
        	.findViewById(R.id.note_list_item_content);
        TextView noteDate = (TextView) viewLayout
        	.findViewById(R.id.note_list_item_date);
        mNoteDelete = (ImageView) viewLayout.findViewById(R.id.note_list_item_delete);
        mNoteEdit = (ImageView) viewLayout.findViewById(R.id.note_list_item_edit);
        
        mTextSize = mStorage.getTextSize();
        
        Note note = treeNodeInfo.getId();
        
        // Set note content with formatting (highlighting, etc)
        noteContent.setText(TodoistTextFormatter.formatText(note.content));
        noteContent.setTextSize(TypedValue.COMPLEX_UNIT_DIP, mTextSize);
        // Linkify any emails, web addresses and phone numbers in the note's content
        Linkify.addLinks(noteContent, Linkify.ALL);
 
        noteDate.setText(note.getPostDateDescription());
        noteDate.setTextSize(TypedValue.COMPLEX_UNIT_DIP, mTextSize - 7);
        
        mNoteDelete.setTag(note);
		mNoteDelete.setOnClickListener(this);
		
        mNoteEdit.setTag(note);
		mNoteEdit.setOnClickListener(this);
        
        viewLayout.setTag(note);

        return viewLayout;
    }
    
   
    @Override
    public Drawable getBackgroundDrawable(final TreeNodeInfo<Note> treeNodeInfo) {
    	return new ColorDrawable(Color.WHITE);
    }

    @Override
    public long getItemId(final int position) {
        return getTreeId(position).id;
    }

	@Override
	public void onClick(View arg0) {
		
		if (arg0 == mNoteDelete) {
			// Delete note icon was clicked
			if (mOnNoteDelete != null) {
				mOnNoteDelete.onNoteDelete((Note)arg0.getTag());
			}
			
		} else if (arg0 == mNoteEdit) {
			// Edit note icon was clicked
			if (mOnNoteEdit != null) {
				mOnNoteEdit.onNoteEdit((Note)arg0.getTag());
			}
		}
	
	}
}
