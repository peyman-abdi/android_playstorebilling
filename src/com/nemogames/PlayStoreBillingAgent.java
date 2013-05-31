package com.nemogames;

import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender.SendIntentException;
import android.content.ServiceConnection;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;

import com.android.vending.billing.IInAppBillingService;
import com.unity3d.player.UnityPlayer;

public class PlayStoreBillingAgent implements NemoActivityListener
{
	public static int		PlayStoreBillingAgentRequestCode = 1988;
	
	public enum PlayStoreBillingEvent
	{
		OnQueryProductsSuccess(1),
		OnQueryProductsFailed(2),
		OnProductPurchaseSuccess(3),
		OnProdcutPurchaseCancel(4),
		OnProductPurchaseFailed(5),
		OnQueryPurchasesSuccess(6),
		OnQueryPurchasesFailed(7);
		
		int val;
		PlayStoreBillingEvent(int v) { val = v; }
		public int getValue() { return val; }
	}

	private String		ListenerGameObject = "";
	private String		ListenerFunction = "";
	private Activity	RootActivity;
	private IInAppBillingService 	mService;
	private ServiceConnection 		mServiceConn;
	
	
	public void		init(String gameobject, String function)
	{
		this.RootActivity = UnityPlayer.currentActivity;
		this.ListenerGameObject = gameobject;
		this.ListenerFunction = function;
	}
	
	//------------------------------------------------------ public
	public void		QueryAvailableItems(final ArrayList items)
	{
		RootActivity.runOnUiThread(new Runnable()
		{
			public void run()
			{
				new AsyncTask<ArrayList, Integer, Integer>()
				{
					@Override
					protected Integer doInBackground(ArrayList... list) 
					{
						for (int i = 0; i < list.length; i++)
						{
							ArrayList item_ids = list[i];
							Bundle querySkus = new Bundle();
							querySkus.putStringArrayList("ITEM_ID_LIST", item_ids);
							try 
							{
								Bundle skuDetails = mService.getSkuDetails(3, 
										   UnityPlayer.currentActivity.getPackageName(), "inapp", querySkus);
								PlayStoreBillingAgent.this.SendUnity3DQueryProductsEvent(skuDetails);
							} catch (RemoteException e) { e.printStackTrace(); }
						}
						return null;
					}
				}.execute(items);	
			}
		});
	}
	
	public void		QueryPurchasedProducts()
	{
		RootActivity.runOnUiThread(new Runnable()
		{
			public void run()
			{
				new AsyncTask<Integer, Integer, Integer>()
				{
					@Override
					protected Integer doInBackground(Integer... list) 
					{
						try 
						{
							Bundle ownedItems = mService.getPurchases(3,
									UnityPlayer.currentActivity.getPackageName(), "inapp", null);
							int response = ownedItems.getInt("RESPONSE_CODE");
							JSONObject obj = new JSONObject();
							if (response == 0) 
							{
							   ArrayList<String> ownedSkus = 
							      ownedItems.getStringArrayList("INAPP_PURCHASE_ITEM_LIST");
							   ArrayList<String> purchaseDataList = 
							      ownedItems.getStringArrayList("INAPP_PURCHASE_DATA_LIST");
							   ArrayList<String> signatureList = 
							      ownedItems.getStringArrayList("INAPP_DATA_SIGNATURE");

							   JSONArray purchased_products = new JSONArray();
							   try
							   {
								   for (int i = 0; i < purchaseDataList.size(); ++i) 
								   {
								      String purchaseData = purchaseDataList.get(i);
								      String signature = signatureList.get(i);
								      String sku = ownedSkus.get(i);
								      JSONObject purchase = new JSONObject();
								      purchase.put(sku, purchaseData);
								      purchased_products.put(purchase);
								   }
							   } catch (JSONException e) { e.printStackTrace(); }
							   
							  try 
							  {
								  obj.put("purchases", purchased_products);
								  obj.put("eid", PlayStoreBillingEvent.OnQueryPurchasesSuccess.getValue());
							  } catch (JSONException e) { e.printStackTrace(); }
							} else
							{
								try 
								{
									obj.put("eid", PlayStoreBillingEvent.OnQueryPurchasesFailed.getValue());
									obj.put("error", PlayStoreBillingAgent.getResponseCode(response).toString());
								} catch (JSONException e) { e.printStackTrace(); }
							}
							PlayStoreBillingAgent.this.SendUnity3DMessage(obj.toString());
						} catch (RemoteException e) { e.printStackTrace(); }
						return null;
					}
				}.execute(0);	
			}
		});
	}
	
	public void		PurchaseProduct(final String productId)
	{
		
		RootActivity.runOnUiThread(new Runnable()
		{
			public void run()
			{
				new AsyncTask<String, Integer, Integer>()
				{
					@Override
					protected Integer doInBackground(String... list) 
					{
						for (String productId : list)
						{
							try 
							{
								Bundle buyIntentBundle = mService.getBuyIntent(3,
										UnityPlayer.currentActivity.getPackageName(),
										productId, "inapp", "");
								if (buyIntentBundle.getInt("RESPONSE_CODE") == 0)	// RESULT_OK
								{
									PendingIntent pendingIntent = buyIntentBundle.getParcelable("BUY_INTENT");
									try {
										RootActivity.startIntentSenderForResult(
												pendingIntent.getIntentSender(),
												PlayStoreBillingAgentRequestCode, new Intent(), Integer.valueOf(0), Integer.valueOf(0),
												Integer.valueOf(0));
									} catch (SendIntentException e) { e.printStackTrace(); }
								} else
								{
									PlayStoreBillingAgent.this.
										SendUnity3DPurchaseResult(buyIntentBundle.getInt("RESPONSE_CODE"), "");
								}
							} catch (RemoteException e) { e.printStackTrace(); }
						}
						return null;
					}
				}.execute(productId);	
			}
		});
	}
	
	//------------------------------------------------------ private
	
	private enum		BillingResponseCode
	{
		RESULT_OK,
		RESULT_USER_CANCELED,
		RESULT_BILLING_UNAVAILABLE,
		RESULT_ITEM_UNAVAILABLE,
		RESULT_DEVELOPER_ERROR,
		RESULT_ERROR,
		RESULT_ITEM_ALREADY_OWNED,
		RESULT_ITEM_NOT_OWNED,
		RESULT_UNKNOWN
	}
	private static BillingResponseCode		getResponseCode(int v)
	{
		switch(v)
		{
		case 0: return BillingResponseCode.RESULT_OK;
		case 1:	return BillingResponseCode.RESULT_USER_CANCELED;
		case 2:	return BillingResponseCode.RESULT_BILLING_UNAVAILABLE;
		case 3:	return BillingResponseCode.RESULT_ITEM_UNAVAILABLE;
		case 4:	return BillingResponseCode.RESULT_DEVELOPER_ERROR;
		case 5:	return BillingResponseCode.RESULT_ERROR;
		case 6: return BillingResponseCode.RESULT_ITEM_ALREADY_OWNED;
		case 7: return BillingResponseCode.RESULT_ITEM_NOT_OWNED;
		}
		return  BillingResponseCode.RESULT_UNKNOWN;
	}
	
	private void		SendUnity3DQueryProductsEvent(Bundle skuDetails)
	{
		BillingResponseCode response = getResponseCode(skuDetails.getInt("RESPONSE_CODE"));
		if (response == BillingResponseCode.RESULT_OK)
		{
			try
			{
				JSONObject obj = new JSONObject();
				JSONArray unity_data = new JSONArray();
				ArrayList<String> products = skuDetails.getStringArrayList("DETAILS_LIST");
				for (String jsonproduct : products)
					unity_data.put(jsonproduct);
				obj.put("products", unity_data);
				obj.put("eid", PlayStoreBillingEvent.OnQueryProductsSuccess.getValue());
				this.SendUnity3DMessage(obj.toString());
			} catch (JSONException e) { e.printStackTrace(); }			
		} else
		{
			try
			{
				JSONObject obj = new JSONObject();
				obj.put("eid", PlayStoreBillingEvent.OnQueryProductsFailed.getValue());
				obj.put("error", response.toString());
			} catch (JSONException e) { e.printStackTrace(); }	
		}
	}
	private void		SendUnity3DPurchaseResult(int response_code, String purchase_data)
	{
		BillingResponseCode code = getResponseCode(response_code);
		JSONObject obj = new JSONObject();
		if (code == BillingResponseCode.RESULT_OK)
		{
			try 
			{
				obj.put("eid", PlayStoreBillingEvent.OnProductPurchaseSuccess.getValue());
				obj.put("data", purchase_data);
			} catch (JSONException e) { e.printStackTrace(); }
		} else if (code == BillingResponseCode.RESULT_USER_CANCELED)
		{
			try 
			{
				obj.put("eid", PlayStoreBillingEvent.OnProdcutPurchaseCancel.getValue());
				obj.put("data", purchase_data);
			} catch (JSONException e) { e.printStackTrace(); }
		} else
		{
			try 
			{
				obj.put("eid", PlayStoreBillingEvent.OnProductPurchaseFailed.getValue());
				obj.put("error", code.toString());
			} catch (JSONException e) { e.printStackTrace(); }			
		}
		this.SendUnity3DMessage(obj.toString());
	}
	
	private void		SendUnity3DMessage(String data)
	{
		UnityPlayer.UnitySendMessage(ListenerGameObject, ListenerFunction, data);
	}
	
	//------------------------------------------------------ interface
	@Override
	public void onRegistered(Bundle savedInstanceState) 
	{
		mServiceConn = new ServiceConnection()
		{
			@Override
			public void onServiceDisconnected(ComponentName name) {
				mService = null;
			}
			
			@Override
			public void onServiceConnected(ComponentName name, IBinder service) {
				mService = IInAppBillingService.Stub.asInterface(service);
			}
		};
		RootActivity.bindService(new 
		        Intent("com.android.vending.billing.InAppBillingService.BIND"), 
		        this.mServiceConn, Context.BIND_AUTO_CREATE);
	}

	@Override
	public void onRestart() 
	{
	}

	@Override
	public void onStart() 
	{
	}

	@Override
	public void onStop() 
	{
	}

	@Override
	public void onPause() 
	{
	}

	@Override
	public void onResume() 
	{
	}

	@Override
	public void onDestroy() 
	{
		if (mServiceConn != null) 
		{
	        RootActivity.unbindService(mServiceConn);
	    }
	}

	@Override
	public void onBackPressed() 
	{
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) 
	{
		if (requestCode == PlayStoreBillingAgentRequestCode)
		{
			int responseCode = data.getIntExtra("RESPONSE_CODE", 0);
			String purchaseData = data.getStringExtra("INAPP_PURCHASE_DATA");
			this.SendUnity3DPurchaseResult(responseCode, purchaseData);
		}
	}

	@Override
	public void onSaveInstanceState(Bundle outState) 
	{
	}
	
}
