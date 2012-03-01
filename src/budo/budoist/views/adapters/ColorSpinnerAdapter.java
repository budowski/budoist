package budo.budoist.views.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

/**
 * ColorSpinner adapter which allows a user to select colors visually
 * @author Yaron Budowski
 *
 */
public class ColorSpinnerAdapter extends ArrayAdapter<Integer> {
	private Context mContext;
	private LayoutInflater mInflater;
	private int mTextViewResourceId;
	
	public ColorSpinnerAdapter(Context context, int textViewResourceId,
			Integer[] objects) {
		super(context, textViewResourceId, objects);
		
		mTextViewResourceId = textViewResourceId;
		mContext = context;
		mInflater = LayoutInflater.from(mContext);
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		if (convertView == null) {
			convertView = mInflater.inflate(android.R.layout.simple_spinner_item, null);
		}
		convertView.setBackgroundColor((0xFF << 24) | this.getItem(position));
		
		return convertView;
	}
	
	@Override
	public View getDropDownView(int position, View convertView, ViewGroup parent) {
		if (convertView == null) {
			convertView = mInflater.inflate(mTextViewResourceId, null);
		}
		convertView.setBackgroundColor((0xFF << 24) | this.getItem(position));
		
		return convertView;
	}
	

}
