package de.dfki.iui.opentok.cordova.plugin;


import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.cordova.CordovaWebView;
import org.apache.cordova.api.CallbackContext;
import org.apache.cordova.api.CordovaInterface;
import org.apache.cordova.api.CordovaPlugin;
import org.apache.cordova.api.LOG;
import org.apache.cordova.api.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.provider.Settings.Secure;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.AbsoluteLayout;
import android.widget.ImageView;

import com.opentok.android.Connection;
import com.opentok.android.OpentokException;
import com.opentok.android.OpentokException.ErrorCode;
import com.opentok.android.Publisher;
import com.opentok.android.Session;
import com.opentok.android.Stream;
import com.opentok.android.Subscriber;

import de.dfki.iui.opentok.R;


/*
 * 	Copyright (C) 2012-2014 DFKI GmbH
 * 	Deutsches Forschungszentrum fuer Kuenstliche Intelligenz
 * 	German Research Center for Artificial Intelligence
 * 	http://www.dfki.de
 * 
 * 	Permission is hereby granted, free of charge, to any person obtaining a 
 * 	copy of this software and associated documentation files (the 
 * 	"Software"), to deal in the Software without restriction, including 
 * 	without limitation the rights to use, copy, modify, merge, publish, 
 * 	distribute, sublicense, and/or sell copies of the Software, and to 
 * 	permit persons to whom the Software is furnished to do so, subject to 
 * 	the following conditions:
 * 
 * 	The above copyright notice and this permission notice shall be included 
 * 	in all copies or substantial portions of the Software.
 * 
 * 	THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS 
 * 	OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF 
 * 	MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. 
 * 	IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY 
 * 	CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, 
 * 	TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE 
 * 	SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

public class OpenTokPlugin  extends CordovaPlugin {

	private static final String AUDIO_ICON_ID_POSTFIX = "__audioIcon";

	private static final String ID_PUBLISHER = "TBPublisher";

	private final static String PLUGIN_NAME = "OpenTokPlugin";

	private final static String ACTION_UPDATE_VIEW 					= "updateView";
	private final static String ACTION_EXEPTION_HANDLER 			= "exceptionHandler";
	private final static String ACTION_TB_TESTING 					= "TBTesting";
	private final static String ACTION_INIT_PUBLISHER 				= "initPublisher";
	private final static String ACTION_DESTROY_PUBLISHER 			= "destroyPublisher";
	private final static String ACTION_INIT_SESSION 				= "initSession";
	private final static String ACTION_STREAM_CREATED_HANDLER 		= "streamCreatedHandler";
	private final static String ACTION_CONNECT 						= "connect";
	private final static String ACTION_STREAM_DISCONNECT_HANDLER 	= "streamDisconnectedHandler";
	private final static String ACTION_SESSION_DISCONNECT_HANDLER 	= "sessionDisconnectedHandler";
	private final static String ACTION_DISCONNECT 					= "disconnect";
	private final static String ACTION_PUBLISH 						= "publish";
	private final static String ACTION_UNPUBLISH 					= "unpublish";
	private final static String ACTION_UNSUBSCRIBE 					= "unsubscribe";
	private final static String ACTION_SUBSCRIBE 					= "subscribe";

	private final static String ACTION_SESSION_CONNECTION_CREATED_HANDLER 		= "sessionConnectionCreatedHandler";
	private final static String ACTION_SESSION_CONNECTION_DESTROYED_HANDLER 	= "sessionConnectionDestroyedHandler";
	
	private final static String ACTION_GET_SESSION_CONNECTION		= "getSessionConnection";
	private final static String ACTION_TOGGLE_AUDIO					= "toggleAudio";
	private final static String ACTION_REFRESH						= "refresh";
	
	private Session _session;
	private Publisher _publisher;

    private boolean statusIsDeviceOnPause;
	
	//TODO make things thread-safe
	private Map<String,Subscriber> subscriberDictionary;
	private Map<String,ImageView> subscriberAudioIconDictionary;
	private Map<String,Stream> streamDictionary;
	
	private PauseStrategy _pauseMode = PauseStrategy.PAUSE_TRANSMISSION;
	
	/**
	 * It <b>recommended</b> to explicitly stop the session before Activity is paused,
	 * of disconnect all subscribers and un-publish.
	 * 
	 * Use this in combination with REMOVE_NATIVE_VIEWS_ON_RESUME
	 *  which will "clean up" any remnants of the session upon
	 *  resuming the Activity.
	 * 
	 * With PAUSE_TRANSMISSION, the plugin will try to automatically pause all
	 * audio/video transmission during the Activity's pause-state.
	 * 
	 * NOTE that currently (vers. 2.0beta2) will fail to resume from PAUSE_TRANSMISSION
	 * cleanly due to (silent) errors when "pausing" subscribers.
	 * 
	 * 
	 * @author russa
	 */
	public static enum PauseStrategy {
		PAUSE_TRANSMISSION,				// pause/resume publisher/subscribers
		REMOVE_NATIVE_VIEWS_ON_RESUME,	// do remove all views on resume (use this if your app automatically disconnects on pause: ensures that all native overlays will get removed on resume)
		DISCONNECT_ON_PAUSE
	}
	
	//for debugging
	private enum DebugLevel {
		DEBUG, INFO, WARN, ERROR, FATAL
	}
	//field for debug-level:
	private DebugLevel _debug = DebugLevel.INFO;
	
	

	private class ViewParams {
		int top;
		int left;
		int width;
		int height;
		int zIndex;
		public ViewParams(int top, int left, int width, int height, int zIndex) {
			super();
			this.top = top;
			this.left = left;
			this.width = width;
			this.height = height;
			this.zIndex = zIndex;
		}

		public LayoutParams create(){
			return createLayoutParams(left, top, width, height);
		}
	}


    private class SynchronizedViewAdministrator{

        private List<View> ViewList;

        public SynchronizedViewAdministrator(){
            ViewList =  new LinkedList<View>();
        }

        /**
         *
         * @param activity this.cordova.getActivity()
         * @param parentView
         * @param view
         * @param params
         */
// TODO: should add a parent-view-layer for opentok-views instead  of using the webview
        public synchronized void addView(Activity activity, final CordovaWebView parentView, final View view, final LayoutParams params){
            activity.runOnUiThread(
                    new Runnable() {
                        @Override
                        public void run() {
                            parentView.addView(view, params);
                            ViewList.add(view);
                        }
                    }
            );
        }

        public synchronized void removeView(Activity activity, final View view){
            activity.runOnUiThread(
                    new Runnable() {
                        @Override
                        public void run() {
                            if(view != null && view.getParent() != null){

                                // remove its child views first
//                                if (!(view instanceof ImageView)) {
//                                    ((ViewGroup) view).removeAllViews();
//                                }

                                // to be safe - check that view again
//                                if(view != null && view.getParent() != null) {
                                    ((ViewGroup) view.getParent()).removeView(view);
//                                }

                                Log.d(PLUGIN_NAME, String.format("Removed view for from Layout."));

                                if (ViewList.indexOf(view) > -1 ) {
//                                    ViewList.remove(ViewList.indexOf(view));
                                    ViewList.remove(view);
                                }
                            } else {
                                // not in layout -> remove it from list
                                if (ViewList.indexOf(view) > -1 ) {
//                                    ViewList.remove(ViewList.indexOf(view));
                                    ViewList.remove(view);
                                }
                            }
                        }
                    }
            );
        }

        public synchronized void removeAllViews(Activity activity){
            activity.runOnUiThread(
                    new Runnable() {
                        @Override
                        public void run() {

                            Object[] viewListArray = ViewList.toArray();


                            for (Object viewObject: viewListArray) {
                                View view = (View) viewObject;
                                if(view != null && (view.getParent() != null)){
                                    // remove its child views first
//                                    ((ViewGroup)view).removeAllViews();

                                    // to be safe - check that view again
//                                    if(view != null && view.getParent() != null) {
                                        ((ViewGroup) view.getParent()).removeView(view);
//                                    }

                                    Log.d(PLUGIN_NAME, String.format("Removed view for from Layout."));

                                    if (ViewList.indexOf(view) > -1 ) {
//                                    ViewList.remove(ViewList.indexOf(view));
                                        ViewList.remove(view);
                                    }
                                } else {
                                    // not in layout -> remove it from list
                                    if (ViewList.indexOf(view) > -1 ) {
//                                    ViewList.remove(ViewList.indexOf(view));
                                        ViewList.remove(view);
                                    }
                                }
                            }
                        }
                    }
            );
        }
    }

	//	private ViewParams publisherViewParams;
	private Map<String, ViewParams> subscriberViewParams; 

	private OpenTokPlugin.Listener mListener = new OpenTokPlugin.Listener();


	private CallbackContext _sessionConnectedCallback;

	private CallbackContext _streamCreatedCallback;

	private CallbackContext _exceptionCallback;
	private CallbackContext _streamDisconnectedCallback;
	private CallbackContext _sessionDisconnectedCallback;
	
	private CallbackContext _sessionConnectionCreatedCallback;
	private CallbackContext _sessionConnectionDestroyedCallback;


	static CordovaInterface _cordova;
	static CordovaWebView _webView;

//    protected static List<View> TokViewList;
    private SynchronizedViewAdministrator viewAdministrator;

	@Override
	public void initialize(CordovaInterface cordova, CordovaWebView webView) {
		_cordova = cordova;
		_webView = webView;
        statusIsDeviceOnPause = false;
        viewAdministrator = new SynchronizedViewAdministrator();
		Log.d(PLUGIN_NAME, "Initialize Plugin");
		super.initialize(cordova, webView);
	}

	private boolean isPublishing = false;
	private boolean isPausedPublishing = false;
	private boolean isPausedPublishingAudio = false;
	private boolean isPausedPublishingVideo = false;

	private List<PausedSubscriber> pausedSubscribers = new LinkedList<OpenTokPlugin.PausedSubscriber>();

	private ImageView _publisherAudioIcon;

	private class PausedSubscriber {
		Subscriber s;
		boolean isAudio;
		boolean isVideo;

		public PausedSubscriber(Subscriber subs){
			this.isAudio = subs.getSubscribeToAudio();
			this.isVideo = subs.getSubscribeToVideo();
			this.s = subs;
			pause();
		}

		public void pause(){
			this.s.setSubscribeToAudio(false);
			this.s.setSubscribeToVideo(false);
		}

		public void resume(){
			this.s.setSubscribeToAudio(this.isAudio);
			this.s.setSubscribeToVideo(this.isVideo);
		}
		
		public boolean isValid(){
			return s != null && s.getStream() != null && s.getStream().getStreamId() != null;
		}
	}
	@Override
	public void onPause(boolean multitasking) {
		
        statusIsDeviceOnPause = true;

//      Log.d("OPENTOK", "pausing...");
        
        if(this._pauseMode == PauseStrategy.DISCONNECT_ON_PAUSE){
	        if (_session != null){
	            _session.disconnect();
	        }
	        this.onDestroy();
        }
        else if(this._pauseMode == PauseStrategy.PAUSE_TRANSMISSION){
        	this.doPause();
        }
        
        super.onPause(multitasking);
//        Log.d("OPENTOK", "paused...");
	}

	private void doPause(){
		
		Log.d("OPENTOK", "pausing publisher and all subscribers...");
		
		this.isPausedPublishingAudio = this._publisher.getPublishAudio();
		this.isPausedPublishingVideo = this._publisher.getPublishVideo();
		this.isPausedPublishing = true;
		
		this._publisher.setPublishAudio(false);
		this._publisher.setPublishVideo(false);
		
		this.pausedSubscribers.clear();
		for(Subscriber s : this.subscriberDictionary.values()){
			
			this.pausedSubscribers.add( new PausedSubscriber(s) );
		}
		
	}


	@Override
	public void onResume(boolean multitasking) {

        statusIsDeviceOnPause = false;
        
//        Log.d("OPENTOK", "Start resuming...");
        if(this._pauseMode == PauseStrategy.REMOVE_NATIVE_VIEWS_ON_RESUME || this._pauseMode == PauseStrategy.DISCONNECT_ON_PAUSE){
//	        Log.d("OPENTOK", "removing views - if any");
	        // remove all views used by opentok - better safe than sorry
	        viewAdministrator.removeAllViews(this.cordova.getActivity());
//	        Log.d("OPENTOK", "removed views.");
        }
        else if(this._pauseMode == PauseStrategy.PAUSE_TRANSMISSION){
        	this.doResume();
        }

		super.onResume(multitasking);
//        Log.d("OPENTOK", "resumed.");
	}
	
	private void doResume(){
		
		Log.d("OPENTOK", "resuming publisher and all subscribers...");
		
		this.isPausedPublishing = false;
		
		this._publisher.setPublishAudio(this.isPausedPublishingAudio);
		this._publisher.setPublishVideo(this.isPausedPublishingVideo);
		
		for(PausedSubscriber ps : this.pausedSubscribers){
			if(ps.isValid())
				ps.resume();
			else {
				
				//cleanup: if subscriber is still in subscriber-map --> do remove it (i.e. un-subscribe)
				String sid = null;
				for(Entry<String,Subscriber> e : this.subscriberDictionary.entrySet()){
					if(e.getValue() == ps.s){
						sid = e.getKey();
						break;
					}
				}
				
				if(sid != null)
					this.doUnsubscribe(sid, true);
			}
				
		}
		this.pausedSubscribers.clear();
		
	}


	@Override
	public void onDestroy() {
        statusIsDeviceOnPause = true;
		if(_publisher != null){
			if(isPublishing){
				this.doUnpublish();
			}
			this.doDestroyPublisher();
		}

		if(subscriberDictionary != null){
			String[] sids = new String[subscriberDictionary.size()];
			sids = subscriberDictionary.keySet().toArray(sids);

			for(String sid : sids){
				this.doUnsubscribe(sid);
			}
		}

		if(_session != null){
			this.doDisconnect();
		}

		super.onDestroy();
	}



	@Override
	public boolean execute(String action, JSONArray args, CallbackContext callbackContext) {
		boolean isHandled = false;

		doDebug(
				args != null? args.toString() : "NO_ARGS" 
						+ (callbackContext.isFinished()? " CALLBACK_FINISHED!" : "")
						, "execute-"+action
				);//FIXME debug

		if (ACTION_UPDATE_VIEW.equals(action)) {
			isHandled = true;

			PluginResult result;
			try {
				String sid 	= args.getString(0); //[command.arguments objectAtIndex:0];
				int top 	= extractDp(args, 1);//args.getInt(1); //[[command.arguments objectAtIndex:1] intValue];
				int left 	= extractDp(args, 2);//args.getInt(2); //[[command.arguments objectAtIndex:2] intValue];
				int width 	= extractDp(args, 3);//args.getInt(3); //[[command.arguments objectAtIndex:3] intValue];
				int height 	= extractDp(args, 4);//args.getInt(4); //[[command.arguments objectAtIndex:4] intValue];
				int zIndex 	= args.getInt(5); //[[command.arguments objectAtIndex:5] intValue];

				result = doUpdateView(sid,top,left,width,height,zIndex);

			} catch (JSONException e) {
				e.printStackTrace();
				String msg = String.format("Error processing arguments for %s: %s - arguments: %s",action, e, args.toString());
				result = new PluginResult(PluginResult.Status.ERROR, msg);
			}

			callbackContext.sendPluginResult(result);
		}
		else if (ACTION_EXEPTION_HANDLER.equals(action)) {
			isHandled = true;

			PluginResult result = this.setExceptionHandler(callbackContext);

			callbackContext.sendPluginResult(result);
		}
		else if (ACTION_STREAM_DISCONNECT_HANDLER.equals(action)) {
			isHandled = true;

			PluginResult result = this.setStreamDisconnectHandler(callbackContext);

			callbackContext.sendPluginResult(result);
		}
		else if (ACTION_SESSION_DISCONNECT_HANDLER.equals(action)) {
			isHandled = true;

			PluginResult result = this.setSessionDisconnectHandler(callbackContext);

			callbackContext.sendPluginResult(result);
		}
		else if (ACTION_TB_TESTING.equals(action)) {
			isHandled = true;

			this.doTBTesting(callbackContext);
		}
		else if (ACTION_INIT_SESSION.equals(action)) {
			isHandled = true;

			PluginResult result;
			try {

				String sessionId 	= args.getString(0);

				result = doInitSession(sessionId);

			} catch (JSONException e) {
				e.printStackTrace();
				String msg = String.format("Error processing arguments for %s: %s - arguments: %s",action, e, args.toString());
				result = new PluginResult(PluginResult.Status.ERROR, msg);
			}

			callbackContext.sendPluginResult(result);
		}
		else if (ACTION_INIT_PUBLISHER.equals(action)) {
			isHandled = true;

			PluginResult result;
			try {

				LOG.i(PLUGIN_NAME, "creating Publisher");
				boolean bpubAudio = true;
				boolean bpubVideo = true;

				// Get Parameters

				int top 	= args.getInt(0); //[[command.arguments objectAtIndex:0] intValue];
				int left 	= args.getInt(1); //[[command.arguments objectAtIndex:1] intValue];
				int width 	= args.getInt(2); //[[command.arguments objectAtIndex:2] intValue];
				int height 	= args.getInt(3); //[[command.arguments objectAtIndex:3] intValue];

				String name = args.getString(4); //[command.arguments objectAtIndex:4];
				if (name.equals("TBNameHolder")) {

					//TODO this usually only works for tab-devices... need to provide a fallback in case this is a phone...
					name = Secure.getString(this.cordova.getActivity().getContentResolver(),Secure.ANDROID_ID);
					//			        name = [[UIDevice currentDevice] name];
				}

				String publishAudio = args.getString(5); //[command.arguments objectAtIndex:5];
				if (publishAudio.equals("false")) {
					bpubAudio = false;
				}
				String publishVideo = args.getString(6); //[command.arguments objectAtIndex:6];
				if (publishVideo.equals("false")) {
					bpubVideo = false;
				}
				int zIndex = args.getInt(7); //[[command.arguments objectAtIndex:7] intValue];

				result = doInitPublisher(top, left, width, height, name, bpubAudio, bpubVideo, zIndex);

			} catch (JSONException e) {
				e.printStackTrace();
				String msg = String.format("Error processing arguments for %s: %s - arguments: %s",action, e, args.toString());
				result = new PluginResult(PluginResult.Status.ERROR, msg);
			}

			callbackContext.sendPluginResult(result);
		}
		else if (ACTION_PUBLISH.equals(action)) {
			isHandled = true;

			PluginResult result = doPublish();

			callbackContext.sendPluginResult(result);
		}
		else if (ACTION_UNPUBLISH.equals(action)) {
			isHandled = true;

			PluginResult result = doUnpublish();

			callbackContext.sendPluginResult(result);
		}
		else if (ACTION_DESTROY_PUBLISHER.equals(action)) {
			isHandled = true;

			PluginResult result = doDestroyPublisher();

			callbackContext.sendPluginResult(result);
		}
		else if (ACTION_STREAM_CREATED_HANDLER.equals(action)) {
			isHandled = true;

			PluginResult result = this.setStreamCreatedHandler(callbackContext);

			callbackContext.sendPluginResult(result);
		}
		else if (ACTION_CONNECT.equals(action)) {
			isHandled = true;

			//			PluginResult result;
			try {

				//			    NSString* tbKey = [command.arguments objectAtIndex:0];
				//			    NSString* tbToken = [command.arguments objectAtIndex:1];
				String tbKey 	= args.getString(0);
				String tbToken 	= args.getString(1);

				//				result = 
				doConnect(tbKey, tbToken, callbackContext);

			} catch (JSONException e) {
				e.printStackTrace();
				String msg = String.format("Error processing arguments for %s: %s - arguments: %s",action, e, args.toString());
				//				result =
				callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, msg));
			}

			//		    callbackContext.sendPluginResult(result);
		}
		else if (ACTION_SUBSCRIBE.equals(action)) {
			isHandled = true;

			PluginResult result;
			try {

				String sid 	= args.getString(0);//[command.arguments objectAtIndex:0];

				int top 	= args.getInt(1);// [[command.arguments objectAtIndex:1] intValue];
				int left 	= args.getInt(2);// [[command.arguments objectAtIndex:2] intValue];
				int width 	= args.getInt(3);// [[command.arguments objectAtIndex:3] intValue];
				int height 	= args.getInt(4);// [[command.arguments objectAtIndex:4] intValue];
				String tmp 	= args.getString(5);// [command.arguments objectAtIndex:5];
				int zIndex 	= args.getInt(6);// [[command.arguments objectAtIndex:6] intValue];

				boolean isSubscribeToVideo = true;
				if(tmp != null){
					tmp = tmp.trim();
					if(tmp.length() > 0)
						isSubscribeToVideo = Boolean.parseBoolean(tmp);
				}

				result = doSubscribe(sid, top, left, width, height, isSubscribeToVideo, zIndex);

			} catch (JSONException e) {
				e.printStackTrace();
				String msg = String.format("Error processing arguments for %s: %s - arguments: %s",action, e, args.toString());
				result = new PluginResult(PluginResult.Status.ERROR, msg);
			}

			callbackContext.sendPluginResult(result);
		}
		else if (ACTION_UNSUBSCRIBE.equals(action)) {
			isHandled = true;

			PluginResult result;
			try {

				String sid 	= args.getString(0);//[command.arguments objectAtIndex:0];

				result = doUnsubscribe(sid);

			} catch (JSONException e) {
				e.printStackTrace();
				String msg = String.format("Error processing arguments for %s: %s - arguments: %s",action, e, args.toString());
				result = new PluginResult(PluginResult.Status.ERROR, msg);
			}

			callbackContext.sendPluginResult(result);
		}
		else if (ACTION_DISCONNECT.equals(action)) {
			isHandled = true;

			PluginResult result = doDisconnect();

			callbackContext.sendPluginResult(result);
		}
		else if (ACTION_SESSION_CONNECTION_CREATED_HANDLER.equals(action)) {
			isHandled = true;

			PluginResult result = this.setSessionConnectionCreatedHandler(callbackContext);

			callbackContext.sendPluginResult(result);
		}
		else if (ACTION_SESSION_CONNECTION_DESTROYED_HANDLER.equals(action)) {
			isHandled = true;

			PluginResult result = this.setSessionConnectionDestroyedHandler(callbackContext);

			callbackContext.sendPluginResult(result);
		}
		else if (ACTION_GET_SESSION_CONNECTION.equals(action)) {
			isHandled = true;

			doGetSessionConnection(callbackContext);
		}
		else if (ACTION_TOGGLE_AUDIO.equals(action)) {
			isHandled = true;

			PluginResult result;
			try {

				String sid 	= args.getString(0);

				result = doToggleAudio(sid);

			} catch (JSONException e) {
				e.printStackTrace();
				String msg = String.format("Error processing arguments for %s: %s - arguments: %s",action, e, args.toString());
				result = new PluginResult(PluginResult.Status.ERROR, msg);
			}

			callbackContext.sendPluginResult(result);
		}
		else if (ACTION_REFRESH.equals(action)) {
			isHandled = true;

			PluginResult result;
			try {

				String sid 	= args.getString(0);

				result = doRefresh(sid);

			} catch (JSONException e) {
				e.printStackTrace();
				String msg = String.format("Error processing arguments for %s: %s - arguments: %s",action, e, args.toString());
				result = new PluginResult(PluginResult.Status.ERROR, msg);
			}

			callbackContext.sendPluginResult(result);
		}

		return isHandled;
	}

	//TODO add generic sendError-method: takes PluginResult -> sends via _exceptionCallback if present (otherwise LOG output)
	private PluginResult setExceptionHandler(CallbackContext callbackContext){
		this._exceptionCallback = callbackContext;

		PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT);
		result.setKeepCallback(true);
		return result;
	}
	
	@SuppressWarnings("serial")
	private class OpenTokPluginError extends Exception{
		public OpenTokPluginError(String errorMessage) {
			super(errorMessage);
		}
	}
	
	private boolean sendException(String errorMessage){
		
		Exception exc = new OpenTokPluginError(errorMessage);
		
		//remove first element form stack trace, since we want the 
		//  calling method as first entry in the stack trace
		//  (and not this method itself)
		StackTraceElement[] stack = exc.getStackTrace();
		exc.setStackTrace(getStackTrace(stack, 1, stack.length - 1));
		
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		exc.printStackTrace(pw);
		errorMessage = sw.toString();
		
		if(this._exceptionCallback != null){
			
			PluginResult excResult = new PluginResult(
					PluginResult.Status.ERROR, errorMessage
			);
			excResult.setKeepCallback(true);
			OpenTokPlugin.this._exceptionCallback.sendPluginResult(excResult);
			
			return true;
		}
		else {
			LOG.e(PLUGIN_NAME, errorMessage, exc);
			return false;
		}
	}
	
	private StackTraceElement[] getStackTrace(StackTraceElement[] original, int startIndex, int endIndex){
		StackTraceElement[] newStack = new StackTraceElement[endIndex - startIndex + 1];
		for(int i=startIndex, j=0; i <= endIndex; ++i, ++j){
			newStack[j] = original[i];
		}
		return newStack;
	};

	private PluginResult setStreamDisconnectHandler(CallbackContext callbackContext){
		
		if(isDebug())
			LOG.d(PLUGIN_NAME, "Adding Stream Destroyed Event Listener");
		
		this._streamDisconnectedCallback = callbackContext;

		PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT);
		result.setKeepCallback(true);
		return result;
	}

	private PluginResult setSessionDisconnectHandler(CallbackContext callbackContext){
		this._sessionDisconnectedCallback = callbackContext;

		PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT);
		result.setKeepCallback(true);
		return result;
	}

	private PluginResult setStreamCreatedHandler(CallbackContext callbackContext){
		
		if(isDebug())
			LOG.d(PLUGIN_NAME, "Adding Stream Created Event Listener");
		
		this._streamCreatedCallback = callbackContext;

		PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT);
		result.setKeepCallback(true);
		return result;
	}
	
	private PluginResult setSessionConnectionCreatedHandler(CallbackContext callbackContext){
		LOG.d(PLUGIN_NAME, "Adding Connection Created (in Session) Event Listener");
		this._sessionConnectionCreatedCallback = callbackContext;

		PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT);
		result.setKeepCallback(true);
		return result;
	}
	
	private PluginResult setSessionConnectionDestroyedHandler(CallbackContext callbackContext){
		LOG.d(PLUGIN_NAME, "Adding Connection Destroyed (in Session) Event Listener");
		this._sessionConnectionDestroyedCallback = callbackContext;

		PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT);
		result.setKeepCallback(true);
		return result;
	}

	@SuppressLint("DefaultLocale")
	private PluginResult doUpdateView(String sid, int top, int left, int width, int height, int zIndex){

		try{//FIXME TEST

			LOG.i(PLUGIN_NAME, String.format("updateView with arguments: sid %s, top %d, left %d, width %d, height %d, zIndex %d", sid, top, left, width, height, zIndex));

			if(_publisher != null && sid.equals(ID_PUBLISHER)){
				LOG.i(PLUGIN_NAME, String.format("The Width is: %d", width));
				//			LayoutParams layoutParams = createLayoutParams(left, top, width, height);
				////			_publisher.getView().setLeft(left);
				////			_publisher.getView().setTop(top);
				//			_publisher.getView().setX(left);
				//			_publisher.getView().setY(top);
				//            publisherViewContainer.addView(mPublisher.getView(), layoutParams);

				//			this.publisherViewParams = new ViewParams(left, top, width, height, zIndex);
				//			LayoutParams params = this.publisherViewParams.create();//this.createLayoutParams(left, top, width, height);
				LayoutParams params = this.createLayoutParams(left, top, width, height);
				//	        _publisher.getView().setLayoutParams(params);//frame = CGRectMake(left, top, width, height);

				doUpdateViewLayoutParams(_publisher.getView(), params);
				//	        _publisher.view.layer.zPosition = zIndex;


				if(_publisherAudioIcon != null){

					LayoutParams iParams = createIconLayoutParams(top, left, width, height);
					int micStatus = getIconResourceForPublisherStatus();
					int micIconVisiblity = getIconVisibilityForPublisherStatus();

					doUpdateViewIcon(_publisherAudioIcon, iParams, micStatus, micIconVisiblity);
				}
			}
			//    
			Subscriber streamInfo = subscriberDictionary.get(sid);

			//    if (streamInfo) {
			//        // Reposition the video feeds!
			//        streamInfo.view.frame = CGRectMake(left, top, width, height);
			//        streamInfo.view.layer.zPosition = zIndex;
			//    }

			if (streamInfo != null) {
				// Reposition the video feeds!

				ViewParams viewParams = new ViewParams(left, top, width, height, zIndex);//subscriberViewParams.get(sid);
				subscriberViewParams.put(sid, viewParams);
				LayoutParams newPosition = this.createLayoutParams(left, top, width, height);//viewParams.create();// this.createLayoutParams(left, top, width, height);
				//		  streamInfo.getView().setLayoutParams(newPosition);
				doUpdateViewLayoutParams(streamInfo.getView(), newPosition);

				ImageView subscriberAudioIcon = subscriberAudioIconDictionary.get(sid);
				if(subscriberAudioIcon != null){

					LayoutParams iParams = createIconLayoutParams(top, left, width, height);
					int speakerStatus = getIconResourceForSubscriberStatus(streamInfo);
					int speakerIconVisiblity = getIconVisibilityForSubscriberStatus(streamInfo);

					doUpdateViewIcon(subscriberAudioIcon, iParams, speakerStatus, speakerIconVisiblity);
				}
			}

			PluginResult result = new PluginResult(
					PluginResult.Status.OK, 
					String.format("updateView [stream %s, top %d, left %d, width %d, height %d, zIndex %d]", sid, top, left, width, height, zIndex)
			);
			result.setKeepCallback(true);
			return result;

		} catch (Exception e){
			String msg = String.format("error during updateView with arguments: sid %s, top %d, left %d, width %d, height %d, zIndex %d", sid, top, left, width, height, zIndex);
			System.err.println(msg);
			e.printStackTrace();
			LOG.e(PLUGIN_NAME, msg, e);
			throw(new RuntimeException("This is a 'proxied' Exception (for 'real' Exception, see first entry in stack-trace)", e));
		}

	}

	private void doUpdateViewIcon(final ImageView icon, final LayoutParams newLayoutParams, final int ressourceId, final int visibility){
		this.cordova.getActivity().runOnUiThread(new Runnable() {
			@Override
			public void run() {
				icon.setLayoutParams(newLayoutParams);
				icon.setImageResource(ressourceId);
				icon.setVisibility(visibility);
			}
		});
	}

	private void doUpdateViewIconStatus(final ImageView icon, final int ressourceId, final int visibility){
		this.cordova.getActivity().runOnUiThread(new Runnable() {
			@Override
			public void run() {
				icon.setImageResource(ressourceId);
				icon.setVisibility(visibility);
			}
		});
	}

	private void doUpdateViewLayoutParams(final View view, final LayoutParams newLayoutParams){
		this.cordova.getActivity().runOnUiThread(new Runnable() {
			@Override
			public void run() {
				view.setLayoutParams(newLayoutParams);
			}
		});
	}

	private PluginResult doInitSession(String sessionId){
		// Create Session
		_session = Session.newInstance(this.cordova.getActivity(), sessionId, this.mListener);
		// Initialize Dictionary, contains DOM info for every stream
		subscriberDictionary = new HashMap<String, Subscriber>();
		streamDictionary = new HashMap<String, Stream>();
		subscriberViewParams = new HashMap<String, OpenTokPlugin.ViewParams>();

		subscriberAudioIconDictionary = new HashMap<String, ImageView>();

		//TODO support multiple sessions?
		_publisher = null;
		mListener.reset();
		_sessionConnectedCallback = null;
		_streamCreatedCallback = null;
		_exceptionCallback = null;
		_streamDisconnectedCallback = null;
		_sessionDisconnectedCallback = null;
		_sessionConnectionCreatedCallback = null;
		_sessionConnectionDestroyedCallback = null;

		// Return Result
		return new PluginResult(PluginResult.Status.OK, "initSession [sessionId "+sessionId+"]");
	}


	private PluginResult doInitPublisher(int top, int left, int width, int height, String name, boolean publishAudio, boolean publishVideo, int zIndex){

		// Publish and set View
		_publisher = Publisher.newInstance(this.cordova.getActivity(), this.mListener, name);

		_publisher.setPublishAudio(publishAudio);
		_publisher.setPublishVideo(publishVideo);
		
		LayoutParams params = this.createLayoutParams(left, top, width, height);

		if(isInfo())
			LOG.i(PLUGIN_NAME, String.format("Adding view for publisher '%s' at (%d,%d), width %d, height %d (layer: %d)", name, left, top, width, height, zIndex));
		
        viewAdministrator.addView(this.cordova.getActivity(), _webView, _publisher.getView(), params);

		_publisherAudioIcon = createPublisherAudioIcon();
		LayoutParams iconPosition = createIconLayoutParams(top, left, width, height);
        viewAdministrator.addView(this.cordova.getActivity(), _webView, _publisherAudioIcon, iconPosition);

		// Return to Javascript
		return new PluginResult(
				PluginResult.Status.OK, 
				String.format("initPublisher [stream '%s' at (%d,%d), width %d, height %d (z-layer: %d)]", name, left, top, width, height, zIndex)
		);
	}



	private ImageView createPublisherAudioIcon() {

		int micStatus = getIconResourceForPublisherStatus();
		int micIconVisiblity = getIconVisibilityForPublisherStatus();

		return createAudioIcon(micStatus, micIconVisiblity);
	}



	private int getIconVisibilityForPublisherStatus() {
		if(_publisher == null){
			return View.INVISIBLE;
		}
		return _publisher.getPublishAudio()? View.INVISIBLE : View.VISIBLE;
	}

	private int getIconResourceForPublisherStatus() {

		if(_publisher == null){
			return R.drawable.opentok_button_mic_off;
		}

		return _publisher.getPublishAudio()? R.drawable.opentok_button_mic_on : R.drawable.opentok_button_mic_off;
	}

	private ImageView createSubscriberAudioIcon(Subscriber subscriber) {

		int speakerStatus = getIconResourceForSubscriberStatus(subscriber);
		int speakerIconVisiblity = getIconVisibilityForSubscriberStatus(subscriber);

		return createAudioIcon(speakerStatus, speakerIconVisiblity);
	}

	private int getIconVisibilityForSubscriberStatus(Subscriber subscriber) {
		if(subscriber == null){
			return View.INVISIBLE;
		}
		return subscriber.getSubscribeToAudio()? View.INVISIBLE : View.VISIBLE;
	}

	private int getIconResourceForSubscriberStatus(Subscriber subscriber) {

		if(subscriber == null){
			return R.drawable.opentok_button_speaker_off;
		}

		return subscriber.getSubscribeToAudio()? R.drawable.opentok_button_speaker_on : R.drawable.opentok_button_speaker_off;
	}

	private ImageView createAudioIcon(int initialResourceId, int initialVisibility) {

		ImageView imgView= new ImageView(this.cordova.getActivity());

		imgView.setImageResource(initialResourceId);
		imgView.setVisibility(initialVisibility);

		return imgView;
	}



	private LayoutParams createIconLayoutParams(int parentViewTop, int parentViewLeft,
			int parentViewWidth, int parentViewHeight) {

		//TODO constants?
		int offsetX = 10;
		int offsetY = 10;

		//TODO constants?
		int iw = 32;
		int ih = 32;

		//TODO verify that icon fits within parent view (-> size)
		int it = parentViewTop + parentViewHeight - ih - offsetY;
		int il = parentViewLeft + parentViewWidth - iw - offsetX;

		return this.createLayoutParams(il, it, iw, ih);
	}

	private PluginResult doDestroyPublisher(){

		ImageView thePublisherAudioIcon = _publisherAudioIcon;
		if(thePublisherAudioIcon != null){
			_publisherAudioIcon = null;
            viewAdministrator.removeView(this.cordova.getActivity(), thePublisherAudioIcon);
		}

		Publisher thePublisher = _publisher;
		if(thePublisher != null){
			_publisher = null;
            viewAdministrator.removeView(this.cordova.getActivity(), thePublisher.getView());
			if(this.isPublishing){
				this.isPublishing = false;
				thePublisher.destroy();
			}
			else {
				this.doDebug("publisher already disposed.", "destroyPublisher");
			}
		}
		else {
			return new PluginResult(
					PluginResult.Status.ERROR, 
					"Could not destroy VIEW for publisher: publisher is NULL!"
			);////////////////// EARLY EXIT //////////////////////////
		}

		return new PluginResult(PluginResult.Status.OK, "destroyPublisher");
	}

	private void doConnect(String key, String token, CallbackContext callbackContext){
        if (!statusIsDeviceOnPause) {
            this._sessionConnectedCallback = callbackContext;
            _session.connect(key, token);
        }
	}

	private PluginResult doDisconnect(){
        _session.disconnect();

//        Log.d("OPENTOK", "removing views - if any");
        // remove all views used by opentok
        // TODO: check if necessary here
        viewAdministrator.removeAllViews(cordova.getActivity());
//        Log.d("OPENTOK", "removed views.");
		return new PluginResult(PluginResult.Status.OK, "disconnect");
	}
	
	private PluginResult doPublish(){
        if (!statusIsDeviceOnPause) {

            _session.publish(_publisher);

            if (_publisherAudioIcon != null) {

                doUpdateViewIconStatus(
                        _publisherAudioIcon,
                        getIconResourceForPublisherStatus(),
                        getIconVisibilityForPublisherStatus()
                );

            } else {
                this.sendException("Could not updated audio status icon for publisher: resource is null");
            }

            return new PluginResult(PluginResult.Status.OK, "publish");
            
        } else {
            return new PluginResult(PluginResult.Status.ERROR, "Could not publsh - Reason: on pause.");
        }
	}

	private PluginResult doUnpublish(){
		
		
		if(this._publisher != null){
			if(this.isPublishing){
				_session.unpublish(this._publisher);
			}
			else if(isInfo()){
				this.doDebug("publisher already disposed.", "unpublish");
			}
		}
		else {
			return new PluginResult(
					PluginResult.Status.ERROR, 
					"Could not unpublish: publisher is NULL!"
			);////////////////// EARLY EXIT //////////////////////////
		}
		
		return new PluginResult(PluginResult.Status.OK, "unpublish");
	}

	private PluginResult doSubscribe(String sid, int top, int left, int width, int height, boolean isSubscribeToVideo, int zIndex){

        if (!statusIsDeviceOnPause) {

            // Acquire Stream, then create a subscriber object and put it into dictionary
            Stream stream = streamDictionary.get(sid);
            Subscriber subscriber = Subscriber.newInstance(this.cordova.getActivity(), stream, this.mListener);
            subscriberDictionary.put(stream.getStreamId(), subscriber);
            subscriber.setSubscribeToVideo(isSubscribeToVideo);

            _session.subscribe(subscriber);

            ViewParams viewParams = new ViewParams(top, left, width, height, zIndex);
            subscriberViewParams.put(stream.getStreamId(), viewParams);
            LayoutParams params = this.createLayoutParams(left, top, width, height);//viewParams.create();//this.createLayoutParams(left, top, width, height);
            //		subscriber.getView().setLayoutParams(params);
            //		((OpenTokExample)this.cordova.getActivity()).addView(subscriber.getView(), params);

			if(isInfo())
				LOG.i(PLUGIN_NAME, String.format("Adding subscriber (stream %s) for publisher at (%d,%d), width %d, height %d (layer: %d)%s", sid, left, top, width, height, zIndex, isSubscribeToVideo ? "" : " IS NOT SUSCRIBING TO VIDEO!"));
            
			viewAdministrator.addView(this.cordova.getActivity(), _webView, subscriber.getView(), params);

            ImageView subscriberAudioIcon = createSubscriberAudioIcon(subscriber);
            subscriberAudioIconDictionary.put(sid, subscriberAudioIcon);
            LayoutParams iParams = this.createIconLayoutParams(top, left, width, height);
            viewAdministrator.addView(this.cordova.getActivity(), _webView, subscriberAudioIcon, iParams);

            // Return to JS event handler
            return new PluginResult(
                    PluginResult.Status.OK,
                    String.format(
                            "subscribe [stream %s at (%d,%d), width %d, height %d (z-layer: %d)%s]",
                            sid, left, top, width, height, zIndex, isSubscribeToVideo ? "" : " _disabled video_ "
                    )
            );
        } else {

            // Return to JS event handler
            return new PluginResult(
                    PluginResult.Status.ERROR,
                    String.format(
                            "could not subscribe to [stream %s at (%d,%d), width %d, height %d (z-layer: %d)%s] -- Reason: device is on pause.",
                            sid, left, top, width, height, zIndex, isSubscribeToVideo ? "" : " _disabled video_ "
                    )
            );
        }
	}

	private PluginResult doUnsubscribe(String sid){
		return doUnsubscribe(sid, false);
	}
	
	private PluginResult doUnsubscribe(String sid, boolean onlyCleanUp){
		Subscriber subscriber = subscriberDictionary.get(sid);
//		subscriber.getStream().getConnection().

		if(subscriber == null){
			
			if(isError())
				LOG.e(PLUGIN_NAME, String.format("unsubscribe(%s): coud not find subscriber for this stream.", sid));
			
			//TODO should this return an error?
			return new PluginResult(
					PluginResult.Status.OK,
					"unsubscribe: no subscriber for stream "+sid
			); ////////////////////////////////////////////////////////////// EARLY EXIT ////////////
		}

		ImageView audioIcon = subscriberAudioIconDictionary.get(sid);
        viewAdministrator.removeView(this.cordova.getActivity(), audioIcon);
		subscriberAudioIconDictionary.remove(sid);

        viewAdministrator.removeView(this.cordova.getActivity(), subscriber.getView());

		if( ! onlyCleanUp){
			_session.unsubscribe(subscriber);
		}
		
		subscriberDictionary.remove(sid);

		// Return to JS event handler
		return new PluginResult(PluginResult.Status.OK, "unsubscribe [stream "+sid+"]");
	}

	private void doTBTesting(CallbackContext callbackContext){

		if(_exceptionCallback == null){
			_exceptionCallback = callbackContext;
		}

		JSONObject response = new JSONObject();
		try {
			response.put("message", "HMMM Test Error!");
		} catch (JSONException e) {
//			e.printStackTrace();
			if(isError())
				LOG.e(PLUGIN_NAME, "Could not create response (no ERROR listener registered)", e);
		}

		PluginResult result = new PluginResult(PluginResult.Status.OK, response);
		result.setKeepCallback(true);

		callbackContext.sendPluginResult(result);
	}

	private PluginResult doToggleAudio(String streamId){

		PluginResult result = null;
		if(_publisher != null && streamId.equals(ID_PUBLISHER)){

			boolean newAudioSetting = ! _publisher.getPublishAudio();

			if(isInfo())
				LOG.i(PLUGIN_NAME, String.format("Toggle audio for publisher: mute %s", newAudioSetting));

			_publisher.setPublishAudio(newAudioSetting);

			doUpdateViewIconStatus(
					_publisherAudioIcon, 
					getIconResourceForPublisherStatus(), 
					getIconVisibilityForPublisherStatus()
					);

			result = new PluginResult(PluginResult.Status.OK, "toggleAudio [stream "+streamId+", publisher]");
		}

		Subscriber streamInfo = subscriberDictionary.get(streamId);
		if (streamInfo != null) {

			boolean newAudioSetting = ! streamInfo.getSubscribeToAudio();

			if(isInfo())
				LOG.i(PLUGIN_NAME, String.format("Toggle audio for Subscriber (stream-ID: %s): mute %s", streamId, newAudioSetting));

			streamInfo.setSubscribeToAudio(newAudioSetting);

			ImageView subscriberAudioIcon = subscriberAudioIconDictionary.get(streamId);
			doUpdateViewIconStatus(
					subscriberAudioIcon, 
					getIconResourceForSubscriberStatus(streamInfo), 
					getIconVisibilityForSubscriberStatus(streamInfo)
					);

			result = new PluginResult(PluginResult.Status.OK, "toggleAudio [stream "+streamId+", subscriber]");
		}

		if(result == null){
			result = new PluginResult(PluginResult.Status.ERROR, "No Subscriber / Publisher for streamId "+streamId);
		}

		return result;
	}

	private PluginResult doRefresh(String streamId){

		PluginResult result = null;
		if(_publisher != null && streamId.equals(ID_PUBLISHER)){

			boolean isAudio = _publisher.getPublishAudio();
			boolean isVideo = _publisher.getPublishVideo();

			_publisher.setPublishAudio(false);
			_publisher.setPublishVideo(false);

			_publisher.setPublishAudio(isAudio);
			_publisher.setPublishVideo(isVideo);

			//BUGFIX there seems to a problem, with the correct positioning ... trigger update through JavaScript:
			this.webView.sendJavascript("if(typeof TB !== 'undefined' && TB.updateViews){ TB.updateViews(); } else {console.error('could not enfore view update for subscribers: missing TB.updateViews() function!');}");

			if(isInfo())
				LOG.i(PLUGIN_NAME, String.format("Refreshing view for publisher..."));
			
			result = new PluginResult(PluginResult.Status.OK, "refresh [stream "+streamId+", publisher]");
		}

		Subscriber streamInfo = subscriberDictionary.get(streamId);
		if (streamInfo != null) {

			//REFRESH: remove and re-add the subscriber

			ViewParams p = subscriberViewParams.get(streamId);
			doUnsubscribe(streamId);

			doSubscribe(streamId, p.top, p.left, p.width, p.height, streamInfo.getSubscribeToVideo(), p.zIndex);
			//BUGFIX there seems to a problem, with the correct positioning ... trigger update through JavaScript:
			this.webView.sendJavascript("if(typeof TB !== 'undefined' && TB.updateViews){ TB.updateViews(); } else {console.error('could not enfore view update for subscribers: missing TB.updateViews() function!');}");

			if(isInfo())
				LOG.i(PLUGIN_NAME, String.format("Refreshing view for Subscriber (stream-ID: %s)...", streamId));

			result = new PluginResult(PluginResult.Status.OK, "refresh [stream "+streamId+", subscriber]");
		}

		if(result == null){
			result = new PluginResult(PluginResult.Status.ERROR, "No Subscriber / Publisher for streamId "+streamId);
		}

		return result;
	}

	private LayoutParams createLayoutParams(int left, int top, int width, int height){
//		FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dpToPx(width), dpToPx(height));
//		params.leftMargin = dpToPx(left);
//		params.topMargin = dpToPx(top);
//		
//		//TODO set zIndex ?
////		params.??
//		return params;

		return new AbsoluteLayout.LayoutParams(dpToPx(width), dpToPx(height), dpToPx(left), dpToPx(top));

//		RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(dpToPx(width), dpToPx(height));
//		params.leftMargin = dpToPx(left);
//		params.topMargin = dpToPx(top);
//		
//		return params;

//		FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
//				FrameLayout.LayoutParams.MATCH_PARENT,
//				FrameLayout.LayoutParams.MATCH_PARENT
//		);
//		params.leftMargin = dpToPx(left);
//		params.topMargin = dpToPx(top);
//		params.height = dpToPx(height);
//		params.width = dpToPx(width);
//		
//		return params;
	}

	private int extractDp(JSONArray args, int index) throws JSONException {
		
		JSONException failure = null;
		int result = 0;
		try {
			result = args.getInt(index);
		} catch (JSONException e) {
			failure = e;
		}

		if(failure != null){
			String temp = args.getString(index);

			//simple decimal number pattern
			Pattern p = Pattern.compile("([+-]?\\d+([,.]\\d+)?)");
			Matcher m = p.matcher(temp);
			if(m.find()){
				result = Integer.parseInt(temp.substring( m.start(), m.end() ));
			}
		}

		return result;
	}

	/**
	 * Converts dp to real pixels, according to the screen density.
	 * @param dp A number of density-independent pixels.
	 * @return The equivalent number of real pixels.
	 */
	private int dpToPx(int dp) {
		double screenDensity = this.cordova.getActivity().getResources().getDisplayMetrics().density;
		return (int) (screenDensity * (double) dp);
	}


	private CallbackContext retrieveSessionConnectionCallback;
	private void doGetSessionConnection(CallbackContext callbackContext){

		retrieveSessionConnectionCallback = callbackContext;

		if(this.mListener.publisherConnectionId != null){
			doSendSessionConnectionData();
		}
		else {
			PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT);
			result.setKeepCallback(true);
			callbackContext.sendPluginResult(result);
		}
	}

	/**
	 * WORKAROUND: the current Android impl. does not provide the session's connection on connecting
	 * 			-> if the JAVASCRIPT code receives the connect-event without a connection for the session
	 * 			   it will register a callback
	 * 			-> when the publisher starts, we use its connection as session-connection
	 * 
	 * If there was no callback set, this method does nothing.
	 * 
	 * After successfully sending the connection-information, the callback-instance will be reset to NULL,
	 * i.e. the information will only be sent 1 time.
	 * 
	 */
	private void doSendSessionConnectionData(){
		
		if(retrieveSessionConnectionCallback != null){
			// After session is successfully connected, the connection property is available
			JSONObject connData = new JSONObject();
			PluginResult result;
			try {

				String sessionConnectionId = this.mListener.getConnectionId(_session);						
				connData.put("connectionId", sessionConnectionId);
				connData.put("data", _session.getConnection().getData());
				String creationTime = String.format("%d", _session.getConnection().getCreationTime().getTime());
				connData.put("creationTime", creationTime);

				result = new PluginResult(PluginResult.Status.OK, connData);
				
			} catch (JSONException e) {
				
				if(isError())
					LOG.e(PLUGIN_NAME, "Could not create response", e);
				
				result = new PluginResult(PluginResult.Status.ERROR, "Could not create response object: "+e);
			}

			retrieveSessionConnectionCallback.sendPluginResult(result);
			retrieveSessionConnectionCallback = null;
		}
	}

	@SuppressLint("DefaultLocale")
	private class Listener implements Session.Listener, Publisher.Listener, Subscriber.Listener {

		private static final String NAME = "OpenTokPlugin.Listener";

		private String publisherConnectionId = null;
		private String publisherStreamId = null;

		public void reset(){
			this.publisherStreamId = null;
			this.publisherConnectionId = null;
		}

		@Override
		public void onSubscriberConnected(Subscriber subscriber) {
			
			if(isInfo())
				Log.i(NAME, "Subscriber connected.");


			if(isDebug()){
				doDebug( debugSubscriber(subscriber, null), "onSubscriberConnected");
				doDebug( debugSession(_session, null), "onSubscriberConnected");
				doDebug( debugPublisher(_publisher, null), "onSubscriberConnected");
			}
		}

		@Override
		public void onSubscriberException(Subscriber subscriber, OpentokException exc) {
			
			if(isWarn())
				LOG.w(NAME, "Subscriber exception", exc);

			if(OpenTokPlugin.this._streamDisconnectedCallback != null){
				
				String connId = subscriber.getStream().getConnection().getConnectionId();
				PluginResult result = new PluginResult(PluginResult.Status.OK, connId);
				result.setKeepCallback(true);
	
				OpenTokPlugin.this._streamDisconnectedCallback.sendPluginResult(result);
				
			} else if(isError()) {
				LOG.e(NAME, "Subscriber exception " + debugSubscriber(subscriber, null), exc);
			}
			
		}

		@Override
		public void onSubscriberVideoDisabled(Subscriber subscriber) {
			
			if(isInfo())
				Log.i(NAME, "The subscriber disabled video.");

		}

		@Override
		public void onPublisherChangedCamera(int id) {
			
			if(isInfo())
				Log.i(NAME, "The publisher changed camera to "+id);

			if(isDebug()){
				doDebug( debugSession(_session, null), "onPublisherChangedCamera_"+id);
				doDebug( debugPublisher(_publisher, null), "onPublisherChangedCamera_"+id);
			}
		}

		@Override
		public void onPublisherException(OpentokException exc) {
			
			if(isWarn())
				LOG.w(NAME, "Publisher didFailWithError", exc);

			if(_exceptionCallback != null){

//				ErrorCode code = exc.getErrorCode();
				JSONObject response = new JSONObject();
				try {
					response.put("message", exc.getMessage());
//					response.put("code", code.getErrorCode());
				} catch (JSONException e) {
//					e.printStackTrace();
					if(isError())
						LOG.e(NAME, "Could not create response", e);
				}

				PluginResult result = new PluginResult(PluginResult.Status.OK, response);
				result.setKeepCallback(true);

				OpenTokPlugin.this._exceptionCallback.sendPluginResult(result);
			}
		}

		@Override
		public void onPublisherStreamingStarted() {
			
			if(isInfo())
				Log.i(NAME, "The publisher started streaming.");

			isPublishing = true;
			publisherStreamId = _publisher.getStreamId();

			if(isDebug()){
				doDebug( debugSession(_session, null), "onPublisherStreamingStarted");
				doDebug( debugPublisher(_publisher, null), "onPublisherStreamingStarted");
			}
		}

		@Override
		public void onPublisherStreamingStopped() {
			
			if(isInfo())
				Log.i(NAME, "The publisher stopped streaming.");

			isPublishing = false;
//	    	publisherStreamId = null;

			if(isDebug()){
				doDebug( debugSession(_session, null), "onPublisherStreamingStopped");
//				doDebug( debugPublisher(_publisher, null), "onPublisherStreamingStopped");
			}

			String key = this.createStreamDroppedResponseDataStr(this.publisherStreamId, this.publisherConnectionId);

			//TODO remove from streamDictionary?

			PluginResult result = new PluginResult(PluginResult.Status.OK, key);
			result.setKeepCallback(true);

			OpenTokPlugin.this._streamDisconnectedCallback.sendPluginResult(result);
		}

		@Override
		public void onSessionConnected() {
			
			if(isInfo())
				LOG.i(NAME, "Session connected");

			if(isDebug()){
				doDebug( debugSession(_session, null), "onSessionConnected");
//				doDebug( debugPublisher(_publisher, null), "onSessionConnected");
			}


			JSONObject response = new JSONObject();
			try {

				// SessionConnectionStatus
				response.put("sessionConnectionStatus", "OTSessionConnectionStatusConnected");//FIXME at this point in the Android SDK, we seem to always have a connected session...

				// SessionId
//				response.put("sessionId", _session.getId());//FIXME use ID from connect-call?
				response.put("connectionCount", "1");//FIXME currently there is only 1 connection per session allowed...

				// SessionStreams
				ArrayList<JSONObject> streamList = new ArrayList<JSONObject>();
				for(Stream stream : streamDictionary.values()){
					JSONObject streamData = createStreamJSON(stream);
					streamList.add(streamData);
				}
				response.put("streams", new JSONArray(streamList));

				// After session is successfully connected, the connection property is available
				JSONObject connData = new JSONObject();

				String sessionConnectionId = this.getConnectionId(_session);
				connData.put("connectionId", sessionConnectionId);

				connData.put("data", _session.getConnection().getData());
				String creationTime = String.format("%d", _session.getConnection().getCreationTime().getTime());
				connData.put("creationTime", creationTime);
				response.put("connection", connData);

				// Session Environment
				// Changed to production by default
				response.put("environment", "production");
				
			} catch (JSONException e) {
				e.printStackTrace();
				LOG.e(NAME, "Could not create response", e);
			}

			// After session dictionary is constructed, return the result!
			PluginResult result = new PluginResult(PluginResult.Status.OK, response);

			OpenTokPlugin.this._sessionConnectedCallback.sendPluginResult(result);
		}

		@Override
		public void onSessionCreatedConnection(Connection connection) {
			
			if(isInfo())
				Log.i(NAME, "Session: created connection, id "+connection.getConnectionId());

			if(isDebug()){
				doDebug( debugConnection(connection, null), "onSessionCreatedConnection");
				doDebug( debugSession(_session, null), "onSessionCreatedConnection");
				doDebug( debugPublisher(_publisher, null), "onSessionCreatedConnection");
			}

			//NOTE: currently, the OpenTok Android library does not seem to trigger this event/listener-method (-> use stream-based events instead)...
			if(OpenTokPlugin.this._sessionConnectionCreatedCallback != null){
				
				JSONObject response = new JSONObject();
				try {
//					response.put("reason", null);
					response.put("type", "connectionCreated");

					//NOTE: using "connection" instead of "connections", since we only ever
					//		have 1 connection here -> the JS event however, should put the in an array for property "connections"
					response.put("connection", createConnectionJSON(connection));
				} catch (JSONException e) {
					e.printStackTrace();
					LOG.e(NAME, "Could not create response", e);
				}

				PluginResult result = new PluginResult(PluginResult.Status.OK, response);
				result.setKeepCallback(true);

				OpenTokPlugin.this._sessionConnectionCreatedCallback.sendPluginResult(result);
			}
		}

		@Override
		public void onSessionDisconnected() {

			if(isInfo())
				LOG.i(NAME, String.format("Session disconnected: (%s)", _session));

			if(isDebug()){
				doDebug( debugSession(_session, null), "onSessionDisconnected");
				doDebug( debugPublisher(_publisher, null), "onSessionDisconnected");
			}

			//FIXME cleanup? clear streamDictionary, subscriberDictionary?
			List<String> subscriberIds = new LinkedList<String>(subscriberDictionary.keySet());
			for(String id : subscriberIds){
				//handle subscribers:
				// * remove subscriber & their view
				// * TODO? dispatch stream destroyed event for subscriber (not cancelable)
				
				doUnsubscribe(id,true);
			}
			
			//handle publisher:
			if(_publisher != null){
				// * TODO? dispatch stream destroyed event for publisher; cancelable -> if NOT canceled, destroy publisher
				doDestroyPublisher();
			}

			JSONObject response = new JSONObject();
			try {
				response.put("reason", "networkDisconnected");
				response.put("type", "sessionDisconnected");
			} catch (JSONException e) {
				e.printStackTrace();
				LOG.e(NAME, "Could not create response", e);
			}

			PluginResult result = new PluginResult(PluginResult.Status.OK, response);
			result.setKeepCallback(true);

			OpenTokPlugin.this._sessionDisconnectedCallback.sendPluginResult(result);
		}

		@Override
		public void onSessionDroppedConnection(Connection connection) {

			if(isInfo())
				Log.i(NAME, "Session: dropped connection, id "+connection.getConnectionId());
			
			if(isDebug()){
				doDebug( debugConnection(connection, null), "onSessionDroppedConnection");
				doDebug( debugSession(_session, null), "onSessionDroppedConnection");
				doDebug( debugPublisher(_publisher, null), "onSessionDroppedConnection");
			}

			//NOTE: currently, the OpenTok Android library does not seem to trigger this event/listener-method (-> use stream-based events instead)...
			if(OpenTokPlugin.this._sessionConnectionDestroyedCallback != null){
				
				JSONObject response = new JSONObject();
				try {
					response.put("reason", "clientDisconnected");
					response.put("type", "connectionDestroyed");

					//NOTE: using "connection" instead of "connections", since we only ever
					//		have 1 connection here -> the JS event however, should put the in an array for property "connections" 
					response.put("connection", createConnectionJSON(connection));
				} catch (JSONException e) {
					e.printStackTrace();
					LOG.e(NAME, "Could not create response", e);
				}

				PluginResult result = new PluginResult(PluginResult.Status.OK, response);
				result.setKeepCallback(true);

				OpenTokPlugin.this._sessionConnectionDestroyedCallback.sendPluginResult(result);
				
			}
		}

		@Override
		public void onSessionDroppedStream(Stream stream) {
			
			if(isInfo())
				LOG.i(NAME, "Dropped Stream");

			if(isDebug()){
				doDebug( debugStream(stream, null), "onSessionDroppedStream");
				doDebug( debugSession(_session, null), "onSessionDroppedStream");
				doDebug( debugPublisher(_publisher, null), "onSessionDroppedStream");
			}
			else if(isInfo()) {
				doDebug( String.format("dropping stream.id %s \t - current_session: publisher.streamId= %s | publisher.connection.connectionId= %s", stream.getStreamId(), this.publisherStreamId, this.publisherConnectionId), "onSessionDroppedStream");
			}

			String key = this.createStreamDroppedResponseDataStr(stream);

			//TODO remove from streamDictionary?

			PluginResult result = new PluginResult(PluginResult.Status.OK, key);
			result.setKeepCallback(true);

			OpenTokPlugin.this._streamDisconnectedCallback.sendPluginResult(result);
		}

		@Override
		public void onSessionException(OpentokException exc) {
			
			if(isInfo())
				LOG.e(NAME, "Session did not Connect", exc);

			if(_exceptionCallback != null){

				ErrorCode code = exc.getErrorCode();
				JSONObject response = new JSONObject();
				try {
					response.put("message", exc.getMessage());
					response.put("code", code.getErrorCode());
				} catch (JSONException e) {
					e.printStackTrace();
					LOG.e(NAME, "Could not create response", e);
				}

				PluginResult result = new PluginResult(PluginResult.Status.OK, response);
				result.setKeepCallback(true);

				OpenTokPlugin.this._exceptionCallback.sendPluginResult(result);
			}
		}

		@Override
		public void onSessionReceivedStream(Stream stream) {
			
			if(isInfo())
				LOG.i(NAME, "Received Stream");

			if(isDebug()){
				doDebug( debugStream(stream, null), "onSessionReceivedStream");
				doDebug( debugSession(_session, null), "onSessionReceivedStream");
//				doDebug( debugPublisher(_publisher, null), "onSessionReceivedStream");
			}

			streamDictionary.put(stream.getStreamId(), stream);

			//			LOG.e(NAME, String.format("onSessionReceivedStream: streamId %s, connnectionId %s \t(publisherStreamId %s)", stream.getStreamId(), stream.getConnection().getConnectionId(),  _publisher.getStreamId()));

			//			Session pubSession = _publisher.getSession();
			//			int camperaId = _publisher.getCameraId();
			if(stream.getStreamId().equalsIgnoreCase(publisherStreamId)){
				//FIXME actually, onSessionConnected we do not yet have the stream of the publisher AND in Android Session.connection has no valid id...
				//		... WORKAROUND: trigger session-connected event again here and use the publisher's stream-id as the session's connection-id
				//						(we need the session's connection id for detecting, if session-received-stream events carry our own stream...)

				if(publisherConnectionId == null){
					publisherConnectionId = stream.getConnection().getConnectionId();

					doSendSessionConnectionData();
				}
			}

			String data = createStreamDroppedResponseDataStr(stream);
			PluginResult result = new PluginResult(PluginResult.Status.OK, data);
			result.setKeepCallback(true);

			OpenTokPlugin.this._streamCreatedCallback.sendPluginResult(result);
		}

		private String getConnectionId(Connection conn){
			//FIXME currently, we cannot get a valid connection ID for the session's connection...
			//		WORDAROUND: set ID to null, if no connection is present (see also doSendSessionConnectionData())

			return conn == null ? null : conn.getConnectionId();
		}

		private String getConnectionId(Session session){
			//FIXME currently, we cannot get a valid connection ID for the session's connection...
			//		WORDAROUND: use the publisher's connection (see doSendSessionConnectionData())
			return publisherConnectionId;
		}

		private String createStreamDroppedResponseDataStr(Stream s){
			String connId = this.getConnectionId(s.getConnection());
			return createStreamDroppedResponseDataStr(s.getStreamId(), connId);
		}

		private String createStreamDroppedResponseDataStr(String streamId, String connectionId){
			return String.format("%s %s", connectionId, streamId);
		}

		private JSONObject createStreamJSON(Stream stream) throws JSONException {
			JSONObject streamData = new JSONObject();
		
			streamData.put("streamId", stream.getStreamId());
		
			Connection conn = stream.getConnection();
			JSONObject connData = createConnectionJSON(conn);
		
			streamData.put("connection", connData);
			return streamData;
		}

		private JSONObject createConnectionJSON(Connection connection) throws JSONException {
			JSONObject connData = new JSONObject();
		
			connData.put("connectionId", this.getConnectionId(connection));
		
			connData.put("data", connection.getData());
			String creationTime = String.format("%d", connection.getCreationTime().getTime());
			connData.put("creationTime", creationTime);
			return connData;
		}

	}

	
	private boolean isDebug(){
		return _debug.ordinal() >= DebugLevel.DEBUG.ordinal();
	}
	private boolean isInfo(){
		return _debug.ordinal() >= DebugLevel.INFO.ordinal();
	}
	private boolean isWarn(){
		return _debug.ordinal() >= DebugLevel.WARN.ordinal();
	}
	private boolean isError(){
		return _debug.ordinal() >= DebugLevel.ERROR.ordinal();
	}
	@SuppressWarnings("unused")
	private void doDebug(String str){
		doDebug(str, null);
	}
	private void doDebug(String str, String prefix){
		String tag = "OpenTokPlugin.DEBUGINFO";
		if(prefix == null || prefix.length() < 1){
			prefix = "";
		}
		else {
			tag += "-" +prefix; 
			prefix += " - ";
		}
		LOG.e(tag, prefix + str);
	}
	private String debugSession(Session s, String linePrefix){

		if(linePrefix == null)
			linePrefix = "";

		if(s == null){
			return linePrefix + "Session NULL !!!!\n"; ////////// EARLY EXIT //////////
		}

		String str = linePrefix + s.getClass() + "\n";

		str += debugConnection(s.getConnection(), linePrefix+" \t");

		return str + linePrefix + "------- session.end ----------\n\n";
	}
	private String debugStream(Stream s, String linePrefix){

		if(linePrefix == null)
			linePrefix = "";

		if(s == null){
			return linePrefix + "Stream NULL !!!!\n"; ////////// EARLY EXIT //////////
		}

		String str = linePrefix + s.getClass() + "\n";

		str += linePrefix + "  id       " + s.getStreamId() + "\n";
		str += linePrefix + "  name     " + s.getName() + "\n";
		str += linePrefix + "  time     " + s.getCreationTime() + "\n";
		str += linePrefix + "  name     " + s.getName() + "\n";

		str += linePrefix + "  v-width  " + s.getVideoWidth() + "\n";
		str += linePrefix + "  v-height " + s.getVideoHeight() + "\n";

		str += debugConnection(s.getConnection(), linePrefix+" \t");

		return str + "\n\n";
	}
	private String debugConnection(Connection c, String linePrefix){

		if(linePrefix == null)
			linePrefix = "";

		if(c == null){
			return linePrefix + "Connection NULL !!!!\n"; ////////// EARLY EXIT //////////
		}

		String str = "";
        str += linePrefix + c.getClass() + "\n";
		str += linePrefix + "  id   " + c.getConnectionId() + "\n";
		str += linePrefix + "  hash " + (c.getConnectionId() != null? c.hashCode() : "NULL") + "\n";
		str += linePrefix + "  data " + c.getData() + "\n";
		str += linePrefix + "  time " + c.getCreationTime() + "\n";

		return str;
	}
	private String debugPublisher(Publisher p, String linePrefix){
		if(linePrefix == null)
			linePrefix = "";

		if(p == null){
			return linePrefix + "Publisher NULL !!!!\n"; ////////// EARLY EXIT //////////
		}

		String str = linePrefix + p.getClass() + "\n";

		str += linePrefix + "  name     " + p.getName() + "\n";
		str += linePrefix + "  camera   " + p.getCameraId() + "\n";
		str += linePrefix + "  stream   " + p.getStreamId() + "\n";
		str += linePrefix + "  v-active " + p.getPublishVideo() + "\n";
		str += linePrefix + "  a-active " + p.getPublishAudio() + "\n";

		str += debugSession(p.getSession(), linePrefix+" \t");

		return str + linePrefix + "------- publisher.end ----------\n\n";
	}
	private String debugSubscriber(Subscriber s, String linePrefix){
		if(linePrefix == null)
			linePrefix = "";

		if(s == null){
			return linePrefix + "Subscriber NULL !!!!\n"; ////////// EARLY EXIT //////////
		}

		String str = linePrefix + s.getClass() + "\n";

		str += linePrefix + "  v-active " + s.getSubscribeToVideo() + "\n";
		str += linePrefix + "  a-active " + s.getSubscribeToAudio() + "\n";

		str += debugStream(s.getStream(), linePrefix+" \t");
		str += debugSession(s.getSession(), linePrefix+" \t");

		return str + linePrefix + "------- subscriber.end ----------\n\n";
	}
}
