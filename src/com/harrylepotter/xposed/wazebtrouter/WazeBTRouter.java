package com.harrylepotter.xposed.wazebtrouter;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.findField;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.AsyncTask;
import android.util.Log;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class WazeBTRouter implements IXposedHookLoadPackage {
	private AudioManager manager;
	private Context context = null;
	private List waitingObjs = new ArrayList<Object>();
	private final String TAG = "WAZE_INTERCEPTOR";

	private BroadcastReceiver mediaStateReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			if(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED.equals(action)) {
				int status = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, AudioManager.SCO_AUDIO_STATE_ERROR );

				if(status == AudioManager.SCO_AUDIO_STATE_CONNECTED){
					Log.d(TAG, "bluetooth SCO device connected.");
					resumeAudio();
				}
			}
		}

	};

	private AsyncTask<Void, Void, Void> task; 


	public void resumeAudio(){
		Log.d(TAG, "resuming non-bt audio");
		for(Object o: waitingObjs){
			synchronized (o){
				o.notify();
			}	
		}
		waitingObjs.clear();
	}

	public void getLeTask(){
		task = new AsyncTask<Void, Void, Void>(){

			@Override
			protected Void doInBackground(Void... params) {

				try {
					Thread.sleep(5000);
					Log.d(TAG, "Re-routing audio to normal device");
					if(manager != null && manager.getMode() == AudioManager.MODE_IN_CALL){

						if(manager.isBluetoothScoOn()){
							manager.setMode(AudioManager.MODE_NORMAL);
							manager.stopBluetoothSco();
							manager.setBluetoothScoOn(false);		
							manager.setMode(AudioManager.MODE_NORMAL);
						}
					}
				} catch (InterruptedException e) {
					e.printStackTrace();
				}

				return null;
			}	
		};
	}

	public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
		if (!lpparam.packageName.equals("com.waze"))
			return;
		getLeTask();

		final Class<?> appService = findClass("com.waze.AppService", lpparam.classLoader);
		final Class<?> soundManager = findClass("com.waze.NativeSoundManager", lpparam.classLoader);
		final Class<?> audioPlayer = findClass("com.waze.NativeSoundManager$WazeAudioPlayer", lpparam.classLoader);		
		final Class<?> completionListener = findClass("com.waze.NativeSoundManager$WazeAudioPlayer$CompletionListener", lpparam.classLoader);

		XposedBridge.hookAllMethods(appService, "getInstance", new XC_MethodHook() {

			@Override
			protected void afterHookedMethod(MethodHookParam param) throws Throwable {
				if(context != null)
					return;

				Method method = appService.getMethod("getAppContext", new Class[0]);
				context = (Context)method.invoke(null,  new Object[0]);
				context.registerReceiver(mediaStateReceiver , new IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED));

			}	
		});

		XposedBridge.hookAllMethods(soundManager, "create", new XC_MethodHook() {

			@Override
			protected void afterHookedMethod(MethodHookParam param) throws Throwable {
				Method method = soundManager.getMethod("getInstance", new Class[0]);
				Object instance = method.invoke(null,  new Object[0]);

				Field _managerField = findField(soundManager, "mAudioManager");
				manager = (AudioManager)_managerField.get(instance);

			}	
		});


		XposedBridge.hookAllMethods(completionListener, "onCompletion", new XC_MethodHook() {
			@Override
			protected void afterHookedMethod(MethodHookParam param) throws Throwable {
				task.cancel(true);
				getLeTask();
				task.execute();
			}
		});



		XposedBridge.hookAllMethods(audioPlayer, "run", new XC_MethodHook() {
			@Override
			protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
				if(manager != null && manager.getMode() != AudioManager.MODE_IN_CALL){

					manager.setMode(AudioManager.MODE_IN_CALL);
					manager.startBluetoothSco();
					manager.setBluetoothScoOn(true);

					synchronized (param.thisObject) {
						waitingObjs.add(param.thisObject);
						param.thisObject.wait();
					}
				}
			}			

		});



	}
}
