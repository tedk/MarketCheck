package net.homeip.tedk.marketcheck;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import android.app.ExpandableListActivity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.ImageView;
import android.widget.TextView;

public class MainActivity extends ExpandableListActivity {

	private static class AppInfo implements Comparable<AppInfo> {

		public static enum Status {
			BAD, UNKNOWN, GOOD
		};

		public final String packageName;
		public final Status status;

		public AppInfo(String packageName, Status status) {
			this.packageName = packageName;
			this.status = status;
		}

		public int compareTo(AppInfo another) {
			int c = this.status.compareTo(another.status);
			if(c == 0) {
				return this.packageName.compareTo(another.packageName);
			} else {
				return c;
			}
		}

		@Override
		public String toString() {
			return packageName;
		}
	}

	private final String[] groups = { "System Apps", "User Apps", "Other Apps" };
	private final List<AppInfo> systemApps = new ArrayList<AppInfo>();
	private final List<AppInfo> userApps = new ArrayList<AppInfo>();
	private final List<AppInfo> otherApps = new ArrayList<AppInfo>();

	private BaseExpandableListAdapter adapter;
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.main_menu, menu);
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch(item.getItemId()) {
			case R.id.menuItemRefresh:
				new Refresh().execute();
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		adapter = new BaseExpandableListAdapter() {

			public boolean isChildSelectable(int groupPosition,
					int childPosition) {
				return true;
			}

			public boolean hasStableIds() {
				return false;
			}

			public View getGroupView(int groupPosition, boolean isExpanded,
					View convertView, ViewGroup parent) {
				View v = getLayoutInflater().inflate(R.layout.list_item,
						parent, false);
				v.findViewById(R.id.listItemImage).setVisibility(View.GONE);
				TextView tv = (TextView) v.findViewById(R.id.listItemText);
				tv.setText(getGroup(groupPosition).toString());
				tv.setPadding(70, 20, 20, 20);
				tv.setTextAppearance(MainActivity.this,
						android.R.style.TextAppearance_DeviceDefault_Large);
				return v;
			}

			public long getGroupId(int groupPosition) {
				return groupPosition;
			}

			public int getGroupCount() {
				return 3;
			}

			public Object getGroup(int groupPosition) {
				return groups[groupPosition];
			}

			public int getChildrenCount(int groupPosition) {
				switch (groupPosition) {
				case 0:
					return systemApps.size();
				case 1:
					return userApps.size();
				case 2:
					return otherApps.size();
				default:
					return -1;
				}
			}

			public View getChildView(int groupPosition, int childPosition,
					boolean isLastChild, View convertView, ViewGroup parent) {
				View v = getLayoutInflater().inflate(R.layout.list_item,
						parent, false);
				AppInfo child = (AppInfo) getChild(groupPosition, childPosition);
				ImageView iv = (ImageView) v.findViewById(R.id.listItemImage);
				switch(child.status) {
					case GOOD:
						iv.setImageResource(R.drawable.checkmark);
						break;
					case BAD:
						iv.setImageResource(R.drawable.redx);
						break;
					case UNKNOWN:
						iv.setImageResource(R.drawable.questionmark);
						break;
				}
				TextView tv = (TextView) v.findViewById(R.id.listItemText);
				tv.setText(child.packageName);
				tv.setPadding(20, 20, 20, 20);
				tv.setTextAppearance(MainActivity.this,
						android.R.style.TextAppearance_DeviceDefault_Small);
				return v;
			}

			public long getChildId(int groupPosition, int childPosition) {
				return childPosition;
			}

			public Object getChild(int groupPosition, int childPosition) {
				switch (groupPosition) {
				case 0:
					return systemApps.get(childPosition);
				case 1:
					return userApps.get(childPosition);
				case 2:
					return otherApps.get(childPosition);
				default:
					return null;
				}
			}
			
		};

		setListAdapter(adapter);
	}
	
	@Override
	public boolean onChildClick(ExpandableListView parent, View v,
			int groupPosition, int childPosition, long id) {
		super.onChildClick(parent, v, groupPosition, childPosition, id);
		startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ((AppInfo) adapter.getChild(groupPosition, childPosition)).packageName)));
		return true;
	}
	
	private final class Refresh extends AsyncTask<Void, Void, Void> {
		
		private ProgressDialog progressDialog;
		private List<ApplicationInfo> apps;
		
		@Override
		protected Void doInBackground(Void... params) {
			for (ApplicationInfo ai : apps) {
				if (ai.sourceDir.startsWith("/system/app")) {
					systemApps.add(new AppInfo(ai.packageName, checkPackage(ai.packageName)));
				} else if (ai.sourceDir.startsWith("/data/app")) {
					userApps.add(new AppInfo(ai.packageName, checkPackage(ai.packageName)));
				} else {
					otherApps.add(new AppInfo(ai.packageName, AppInfo.Status.UNKNOWN));
				}
				publishProgress();
			}
			return null;
		}
		@Override
		protected void onPostExecute(Void result) {
			super.onPostExecute(result);
			progressDialog.dismiss();
			progressDialog = new ProgressDialog(MainActivity.this);
			progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
			progressDialog.setIndeterminate(true);
			progressDialog.setMessage("Sorting...");
			progressDialog.setCancelable(false);
			progressDialog.show();
			Collections.sort(systemApps);
			Collections.sort(userApps);
			Collections.sort(otherApps);
			adapter.notifyDataSetChanged();
			progressDialog.dismiss();
		}
		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			progressDialog = new ProgressDialog(MainActivity.this);
			progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
			progressDialog.setIndeterminate(true);
			progressDialog.setMessage("Loading...");
			progressDialog.setCancelable(false);
			progressDialog.show();
			
			apps = getPackageManager().getInstalledApplications(0);
			systemApps.clear();
			userApps.clear();
			otherApps.clear();
			
			progressDialog.dismiss();
			progressDialog = new ProgressDialog(MainActivity.this);
			progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
			progressDialog.setMax(apps.size());
			progressDialog.setProgress(0);
			progressDialog.setIndeterminate(false);
			progressDialog.setMessage("Checking Status...");
			progressDialog.setCancelable(false);
			progressDialog.show();
		}
		
		@Override
		protected void onProgressUpdate(Void... values) {
			super.onProgressUpdate(values);
			progressDialog.incrementProgressBy(1);
		}
	}

	private AppInfo.Status checkPackage(String packageName) {
		AppInfo.Status status = AppInfo.Status.UNKNOWN;
		HttpURLConnection huc = null;
		//OutputStream os = null;
		try {
			URL u = new URL("https://play.google.com/store/apps/details?id=" + packageName);
			huc = (HttpURLConnection) u.openConnection();
			//huc.setRequestMethod("GET");
			huc.connect();
			//os = huc.getOutputStream();
			int code = huc.getResponseCode();
			if(code == 200) {
				status = AppInfo.Status.GOOD;
			} else {
				status = AppInfo.Status.BAD;
			}
		} catch (Exception e) {
			Log.d("CheckPackage", "Exception: " + e.toString());
		} finally {
			//if(os != null)
				//try { os.close(); } catch (IOException e) {}
			if(huc != null)
				huc.disconnect();
		}
		return status;
	}

}