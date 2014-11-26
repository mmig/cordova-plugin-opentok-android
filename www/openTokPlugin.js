
(function() {
  var TBError, TBGenerateDomHelper, TBGetZIndex, TBPublisher, TBSession, TBSubscriber, TBSuccess, TBUpdateObjects, getPosition, replaceWithObject,
    _this = this,
    streamElements, _logLevel, _logLevels, _logLevelNames, _logLevelNamesLower, _log;
  
  
  console.info('INIT TB OpenTok plugin for Cordova JS... ');//debug
  
  streamElements = {};
  
  //internal fields / constants for logging
  _logLevels = { 'DEBUG': 0, 'LOG': 1, 'INFO': 2, 'WARN': 3, 'ERROR': 4, 'NONE': 5};
  _logLevelNames		= [ 'DEBUG', 'LOG', 'INFO', 'WARN', 'ERROR', 'NONE'];
  _logLevelNamesLower 	= [ 'debug', 'log', 'info', 'warn', 'error', 'none'];
  _logLevel = _logLevels['DEBUG'];
  
  /**
   * Writes message to console, using the requested logLevel 
   * (if it is lower or equal the currently set logging-level).
   * 
   * @param {any} message 
   * 				the message
   * @param {Number} logLevel 
   * 				one of the log-levels [0-4]
   */
  _log = function(msg, logLevel){
//	  console.error('TB._log['+_logLevelNamesLower[logLevel]+' ?<--? '+_logLevelNamesLower[_logLevel]+']: '+msg);
	  if(_logLevel < 5 && logLevel >= _logLevel){
		  console[ _logLevelNamesLower[logLevel] ](msg);
	  }
  };

  (function(){
	  
	  //REQUIRE String.trim in isPixelValue(): 
	  if( ! String.prototype.trim){
		  String.prototype.trim = function(){
			  return this
	   			.replace(/^\s\s*/, '') //remove whitespace at start...
	   			.replace(/\s\s*$/, '');//... and whitespace at the end of the String
		  };
	  }
	  
  })();
  
  var isPixelValue = function(value){
	  
	  if(typeof value === 'number'){
		  return true;/////////// EARLY EXIT /////////////
	  }
	  
	  if(typeof value === 'undefined'){
		  return false;/////////// EARLY EXIT /////////////
	  }
	  
	  if(typeof value !== 'string'){
		  value = value.toString();
	  }
	  
	  value = value.trim();
	  
	  //does not end with a digit?
	  if( ! /.*\d$/igm.test(value)){
//		//allowed number non-pixel values in CSS:
//		  //pt, pc, in, mm, cm, em, ex, %
//		  var lastChar = value.substring(value.length-2);
//		  console.log('TEST last char: '+lastChar);
		  
		  //if ends with px -> it is a pixel value
		  if( /.*px$/igm.test(value)) {
			  return true;
		  }
		  else {
			  return false;
		  }
		  
	  }
	  
	  return true;
  };
  
  /**
   * Get position and dimension for a DIV element (in pixel).
   * 
   * Returns the actual dimensions, if the height/width
   * were not explicitly set (i.e. default values were used). 
   * 
   * @param divName {String} the ID of the DIV element
   * 
   * @return {Object} an object with fields:
   * 	<ul>
   * 		<li><strong>top:</strong> offset of the element to the top (in px) </li>
   * 		<li><strong>left:</strong> offset of the element to the left (in px) </li>
   * 		<li><strong>width:</strong> the width of the element (in px) </li>
   * 		<li><strong>height:</strong>  the height of the element (in px) </li>
   * 	</ul>
   */
  getPosition = function(divName) {
    var curleft, curtop, height, pubDiv, width;
    pubDiv = document.getElementById(divName);
    width = pubDiv.style.width;
    height = pubDiv.style.height;
    
    //FIXME MOD start
    if(pubDiv.getAttribute('data-default-height') === 'true'){
    	_log('getPosition('+divName+'): default height ('+height+'), using parent height instead: '+pubDiv.parentNode.clientHeight, _logLevels['DEBUG']);
    	height = pubDiv.parentNode.clientHeight;
    }
    else {
    	if( ! isPixelValue(height) ){
        	console.log('getPosition('+divName+'): non-pixel value for height: '+height+', retrieving pixel-height ...');
    		height = pubDiv.clientHeight;
    	}
    	console.log('getPosition('+divName+'): using non-default height: '+height);
    }
    
    if(pubDiv.getAttribute('data-default-width') === 'true'){
    	_log('getPosition('+divName+'): default width ('+width+'), using parent width instead: '+pubDiv.parentNode.clientWidth, _logLevels['DEBUG']);
    	width = pubDiv.parentNode.clientWidth;
    }
    else {
    	if( ! isPixelValue(width) ){
        	_log('getPosition('+divName+'): non-pixel value for width: '+width+', retrieving pixel-width ...', _logLevels['DEBUG']);
    		width = pubDiv.clientWidth;
    	}
    	_log('getPosition('+divName+'): using non-default width: '+width, _logLevels['DEBUG']);
    }
    //FIXME MOD end

    curtop = curleft = 0;
    
    //----- use (slightly modified) jQuery.offset code for calculating the position ---------------------- 
    var docElem, win,
    elem = pubDiv, //this[ 0 ],
    box = { top: 0, left: 0 },
    doc = elem && elem.ownerDocument;

    if ( !doc ) {
    	return;
    }

    docElem = doc.documentElement;
    if ( typeof elem.getBoundingClientRect !== 'undefined'){
    	box = elem.getBoundingClientRect();
    }
    else {
    	//use "old" implementation of openTokPluing.js:
    	scrolltop = scrollleft = 0;
        if (pubDiv.offsetParent) {
          box.left += pubDiv.offsetLeft + pubDiv.scrollLeft;
          box.top  += pubDiv.offsetTop  + pubDiv.scrollTop;
          
          while ((pubDiv = pubDiv.offsetParent)) {
        	box.left += pubDiv.offsetLeft + pubDiv.scrollLeft;
            box.top  += pubDiv.offsetTop  + pubDiv.scrollTop;
          }
        }
    }
    win = window;
    curleft = box.left + win.pageXOffset - docElem.clientLeft;
    curtop  = box.top  + win.pageYOffset - docElem.clientTop;
    //-------------------

    return {
    	top: curtop,
    	left: curleft,
    	width: width,
    	height: height
    };
  };
  /**
   * Replaces the target DOM element with specialized
   * DIV element that "houses" the OpenTok video overlay
   * element.
   * 
   * The stream-element's properties are set along with
   * setting the stream's ID as an attribute and registering
   * a listener for click-events:
   * 	this default handler activates/deactivates (i.e. toggles)
   * 	the sound setting:
   * 		for own camera: toggle microphone
   * 		for others streams: toggle (mute) speakers
   * 
   * 
   * @param divName {String}
   * 			the ID of the target DOM element that should be replaced
   * 			 (if it already was replaced before, the existing one is
   * 			  returned without changing / modifying it).
   * @param streamID {String}
   * 			the ID of the stream, for which a DOM element
   * 			 should be created.
   * @param properties {Object} 
   * 			properties for the stream-element. Currently
   * 			 the following properties are supported / set:
   *  			
   *  			properties.width  {Number}: REQUIRED the width for the DOM element
   *  			properties.height {Number}: REQUIRED the height for the DOM element
   *  			properties.isDefaultWidth {Boolean}: OPTIONAL if true, the element's 
   *  													width is ignored for the overlay
   *  													video, and its parent's current
   *  													width is used instead.
   *  			properties.isDefaultHeight {Boolean}: OPTIONAL if true, the element's 
   *  													height is ignored for the overlay
   *  													video, and its parent's current
   *  													height is used instead.
   *  			properties.width {Number}: REQUIRED the width for the DOM element	
   * 
   * @returns {DOMObject} the created DOM object that represents the video stream.			
   */
  replaceWithObject = function(divName, streamId, properties) {
	  
	  if(typeof streamId === 'undefined' || streamId === 'undefined'){//FIXME MOD
	    	if(_logLevel <= _logLevels['WARN']){
	    		_log('JS replaceWithObject(): UNDEFINED STREAM ID! - divName '+divName+' (newId: '+newId+'), streamId '+streamId+', properties '+(JSON? JSON.stringify(properties) : properties)+', stack-trace: '+(new ReferenceError()).stack, _logLevels['WARN']);
	    	}
	    	streamId = 'undefined';
	  }
	  
    var element, internalDiv;
    element = document.getElementById(divName);
    if( ! element){
    	return null;
    }
    element.setAttribute("class", "OT_root");
    element.setAttribute("data-streamid", streamId);
    
    //MOD use parent DIV size (-> heuristics for using parent's CSS style...)
    //    if DIV was create with default height/width (i.e. no value were specified on creation of the the DIV)
    //    -> mark these DIVs -> when calculation dimensions, use parent DIV's size instead (see getPosition())
    //DISABLED -> this requires that the view-DIVs will explicitly be placed in a styled parent-DIV
    //         (which, in general, is not the case... enable this, if you use parent-DIVs for styling the view-DIVs...)
//    if(properties.isDefaultHeight === true){//FIXME MOD
//    	element.setAttribute('data-default-height', 'true');
//  	  _log('replaceWithObject: replacing existing obj. using default height!', _logLevels['DEBUG']);
//    }
//    if(properties.isDefaultWidth === true){//FIXME MOD
//    	element.setAttribute('data-default-width', 'true');
//  	  _log('replaceWithObject: replacing existing obj. using default width!', _logLevels['DEBUG']);
//    }
    
    element.style.width = properties.width + "px";
    element.style.height = properties.height + "px";
    streamElements[streamId] = element;
    internalDiv = document.createElement("div");
    internalDiv.setAttribute("class", "OT_video-container");
    internalDiv.style.width = "100%";
    internalDiv.style.height = "100%";
    internalDiv.style.left = "0px";
    internalDiv.style.top = "0px";
    element.appendChild(internalDiv);
    

    //FIXME MOD
    internalDiv.addEventListener('click', function(event){
	  	  event.preventDefault();
	  	  if(event.stopImmediatePropagation){
	  		  event.stopImmediatePropagation();
	  	  }
	  	  _log('clicked! \n streamId '+streamId+'\n id '+divName, _logLevels['DEBUG']);
	  	  TBToggleAudio(streamId);
	  	  return false;
	  	}, false
    );
    
    return element;
  };
  
  document.addEventListener('scroll', function(event){
//	  console.debug('scrolled, update views!');
	  TBUpdateObjects();
	}, false
  );
  document.addEventListener('resize', function(event){
//	  console.debug('resized, update views!');
	  TBUpdateObjects();
	}, false
  );

  TBError = function(error) {

	  var msg = 'TBPluginError: ' + (error? (error.stack ? error.stack : error) : error);
	  if(TBExceptionHandlerList && TBExceptionHandlerList.length > 0){
		  TBExceptionHandler(msg);
	  }
	  else {
		  _log(msg, _logLevels['ERROR']);
	  }
  };

  TBSuccess = function(data) {
    _log("TBPluginSuccess: "+JSON.stringify(data), _logLevels['LOG']);
  };

  TBUpdateObjects = function() {
    var e, id, objects, position, streamId, _i, _len;
//    console.log("JS: Objects being updated in TBUpdateObjects");
    objects = document.getElementsByClassName('OT_root');
    for (_i = 0, _len = objects.length; _i < _len; _i++) {
      e = objects[_i];
      _log("JS: Object ("+(_i+1)+" / "+_len+") updated", _logLevels['DEBUG']);
//      streamId = e.getAttribute('streamId');
      streamId = e.dataset.streamId;
      id = e.id;
      position = getPosition(id);
      
      //FIXME HACK for retrieving the data-attribute streamId
      var streamId2 = e.getAttribute('streamId');
      var streamId3 = e.getAttribute('data-streamId');
      
      if(_logLevel <= _logLevels['DEBUG']){
    	  console.debug('JS TBUpdateObjects(): DEBUG, streamId2[attr.streamId] '+streamId2+', streamId3[attr.data-streamId] '+streamId3, _logLevels['DEBUG']);
      }
      
      if(!streamId){
    	  if(!streamId2){
    		  streamId = streamId3;
          }
    	  else {
    		  streamId = streamId2;
    	  }
      }
      
      if(_logLevel <= _logLevels['DEBUG']){
    	  _log('JS TBUpdateObjects(): ARGS - id '+id+', streamId '+streamId+', properties '+(JSON? JSON.stringify(position) : position), _logLevels['DEBUG']);
      }
      
      
      Cordova.exec(TBSuccess, TBError, "TokBox", "updateView", [streamId, position.top, position.left, position.width, position.height, TBGetZIndex(e)]);
    }
  };

  TBGenerateDomHelper = function() {
    var div, domId;
    domId = "PubSub" + Date.now();
    div = document.createElement('div');
    div.setAttribute('id', domId);
    document.body.appendChild(div);
    return domId;
  };
  
  TBToggleAudio = function(streamId){
	  _log('JS TBToggleAudio, streamId '+streamId, _logLevels['DEBUG']);
	  Cordova.exec(TBSuccess, TBError, "TokBox", "toggleAudio", [streamId]);
  };

  var TBExceptionHandlerList = [];
  var TBRemoveExceptionHandler = function(handler){
	  for(var i = TBExceptionHandlerList.length-1; i >= 0; --i){
  		if(TBExceptionHandlerList[i]==handler){
  			TBExceptionHandlerList.splice(i, 1);
  			return true;
  		}
	  }
	  return false;
  };
  var TBExceptionHandler = function(event){
	  
	  for(var i = TBExceptionHandlerList.length-1; i >= 0; --i){
		  TBExceptionHandlerList[i](event);
	  }
	  
  };
  window.TB = {
	/**
	 * Update position and size for DOM elements that
	 * represent streams.
	 * 
	 * This updates the video overlays.
	 * 
	 * NOTE: this is non-API function (i.e. not part of
	 * 		 the official OpenTok API).
	 * 
	 * @returns VOID
	 */
    updateViews: function() {
      return TBUpdateObjects();
    },
    /**
     * 
     * @param event String event name (NOTE: only <tt>exception</tt> is supported)
     * @param handler Function the callback function / handler for the event; 
     * 							takes one parameter (the event object) as argument.
     * 
     */
    addEventListener: function(event, handler) {
      if (event === "exception") {
        _log("JS: TB Exception Handler added", _logLevels['DEBUG']);
        TBExceptionHandlerList.push(handler);
        //TODO only register once
        return Cordova.exec(TBExceptionHandler, TBError, "TokBox", "exceptionHandler", []);
      }
    },
    removeEventListener: function(event, handler) {
        if (event === "exception") {
          _log("JS: TB Exception Handler removed", _logLevels['DEBUG']);
          TBRemoveExceptionHandler(handler);
          //TODO un-register, if no handler is registered anymore?
//          return Cordova.exec(TBExceptionHandler, TBError, "TokBox", "exceptionHandler", []);
        }
      },
    initSession: function(sid) {
      return new TBSession(sid);
    },
    initPublisher: function(one, two, three) {
      var domId, objDiv;
      if ((three != null)) {
        return new TBPublisher(one, two, three);
      }
      if ((two != null)) {
        if (typeof two === "object") {
          objDiv = document.getElementById(one);
          if (objDiv != null) {
            return new TBPublisher("", one, two);
          }
          domId = TBGenerateDomHelper();
          return new TBPublisher(one, domId, two);
        } else {
          return new TBPublisher(one, two, {});
        }
      }
      objDiv = document.getElementById(one);
      if (objDiv != null) {
        return new TBPublisher("", one, {});
      } else {
        domId = TBGenerateDomHelper();
        return new TBPublisher(one, domId, {});
      }
    },

    /**
     * DEFAULT: TB.ERROR
     * 
     * @param {number} logLevel 
     * 					the degree of logging desired by the developer:
	    TB.NONE — API logging is disabled.
	    TB.ERROR — Logging of errors only.
	    TB.WARN — Logging of warnings and errors.
	    TB.INFO — Logging of other useful information, in addition to warnings and errors.
	    TB.LOG — Logging of TB.log() messages, in addition to OpenTok info, warning, and error messages.
	    TB.DEBUG — Fine-grained logging of all API actions, as well as TB.log() messages.
	*/
    setLogLevel: function setLogLevel(logLevel) {
		_logLevel = logLevel;
    },
    /**
     * internal helper: 
     * if logLevel is a valid number, this logLevel is set "permanently" in that, 
     * that setLogLevel() will have no effect.
     * 
     * calling this function with an invalid number as argument (e.g. with null or no argument specified)
     * will reset the function of setLogLevel
     */
    _overwriteLogLevel: function(logLevel) {

    	//valid value: active _overwrite
    	if(typeof logLevel === 'number'){
    		
    		if(this.setLogLevel.name === 'setLogLevel'){
    			// --> not activated before, so store original setting now
    			var originalFunc = this.setLogLevel;
    			this.setLogLevel = function dummyLogLevel(ll){
    	    		this.setLogLevel._storedLogLevel = ll;
    	    	};
    	    	this.setLogLevel._storedLogLevel = _logLevel;
    	    	this.setLogLevel._originalFunc = originalFunc ;
    		}
    		
    		_logLevel = logLevel;
    	}
    	//invalid value: deactivate _overwrite
    	else {
    		if(this.setLogLevel.name === 'dummyLogLevel'){
    			_logLevel = this.setLogLevel._storedLogLevel;
    			this.setLogLevel = this.setLogLevel._originalFunc;
    		}
		}
    }
    ,NONE:	_logLevels['NONE']
    ,ERROR:	_logLevels['ERROR']
    ,WARN:	_logLevels['WARN']
    ,INFO:	_logLevels['INFO']
    ,LOG:	_logLevels['LOG']
    ,DEBUG:	_logLevels['DEBUG']
    ,
    /**
     * Logs message if logLevel is TB.LOG or TB.DEBUG
     * 
     * @param {String} message
     */
    log: function(message){
    	if(_logLevel <= this.LOG){
    		console.log('TB.log: ' + message);
    	}
    }
    //TODO implement:
//   , log: function(message){}
//    /** Checks if the system supports OpenTok for WebRTC.
//     *	returns: Whether the system supports OpenTok for WebRTC (1) or not (0).
//     */
//   , checkSystemRequirements: function() {}//returns Number
//    /** Displays information about system requirments for OpenTok for WebRTC. This information is displayed in an iframe element that fills the browser window.*/ 
//   , upgradeSystemRequirements: function() {}
  };

  window.TBTesting = function(handler) {
    return Cordova.exec(handler, TBError, "TokBox", "TBTesting", []);
  };

  TBGetZIndex = function(ele) {
    var val;
    while ((ele != null)) {
      val = document.defaultView.getComputedStyle(ele, null).getPropertyValue('z-index');
//      console.log(val);
      if (parseInt(val)) {
        return val;
      }
      ele = ele.offsetParent;
    }
    return 0;
  };

  TBPublisher = (function() {

  function TBPublisher(key, domId, properties, session) {
      var height, name, position, publishAudio, publishVideo, width, zIndex, _ref, _ref1, _ref2;
      this.key = key;
      this.domId = domId;
      if(session){
    	  this.session = session;
      }
      this.properties = properties != null ? properties : {};
      if(_logLevel <= _logLevels['DEBUG']){
    	  _log("JS: Publish Called with key "+key+", domId "+domId+", properties "+ (JSON ? JSON.stringify(this.properties): this.properties.toString()), _logLevels['DEBUG']);
      }
      width = 160;
      height = 120;
      name = "TBNameHolder";
      publishAudio = "true";
      publishVideo = "true";
      zIndex = TBGetZIndex(document.getElementById(this.domId));
      if ((this.properties != null)) {
        width = (_ref = this.properties.width) != null ? _ref : 160;
        height = (_ref1 = this.properties.height) != null ? _ref1 : 120;
        name = (_ref2 = this.properties.name) != null ? _ref2 : "";
        if ((this.properties.publishAudio != null) && this.properties.publishAudio === false) {
          publishAudio = "false";
        }
        if ((this.properties.publishVideo != null) && this.properties.publishVideo === false) {
          publishVideo = "false";
        }
      }
      this.obj = replaceWithObject(this.domId, "TBPublisher", {
        width: width,
        height: height
      });
      if( ! this.obj){
    	  _log('OpenTokPlugin.TBPublisher(): Could not create DIV for Publisher', _logLevels['WARN']);
    	  return;///////////////// EARLY EXIT ///////////////////////
      }
      position = getPosition(this.obj.id);
      TBUpdateObjects();
      Cordova.exec(TBSuccess, TBError, "TokBox", "initPublisher", [position.top, position.left, width, height, name, publishAudio, publishVideo, zIndex]);
    }

    TBPublisher.prototype.destroy = function() {
      return Cordova.exec(TBSuccess, TBError, "TokBox", "destroyPublisher", []);
    };
    
    //TODO need to support events for publisher -> taken from jquery-plugin (see _addPublisherEventListeners: function (publisher) @ ~ line 300): 
//    publisher.addEventListener("accessAllowed", function (e) {...});
//    publisher.addEventListener("accessDenied", function (e) {...});
//    publisher.addEventListener("deviceInactive", function (e) {...});
//    publisher.addEventListener("resize", function (e) {...});
//    publisher.addEventListener("settingsButtonClick", function (e) { ... });
    //FIXME for now, only add a dummy:
    TBPublisher.prototype.addEventListener = function dummyAddEventListener(eventName, func) {
    	console.warn('OpenTok Cordova Plugin - JS Interface: not yet implemented Publisher.addEventListener: '+eventName);
    };
    
    return TBPublisher;

  })();

  TBSession = (function() {

    function TBSession(sessionId) {
      var _this = this;
      window.TB.session = _this;//FIXME TEST remove after testing!
      this.sessionId = sessionId;
      this.connect = function(apiKey, token, properties) {
        if (properties == null) {
          properties = {};
        }
        return TBSession.prototype.connect.apply(_this, arguments);
      };
      Cordova.exec(TBSuccess, TBSuccess, "TokBox", "initSession", [this.sessionId]);
    }

    TBSession.prototype.cleanUpDom = function() {
      var e, objects, _i, _len, _results;
//      objects = document.getElementsByClassName('TBstreamObject');
      objects = document.getElementsByClassName('OT_root');
      _results = [];
      for (_i = 0, _len = objects.length; _i < _len; _i++) {
        e = objects[_i];
        _results.push(e.parentNode.removeChild(e));
      }
      return _results;
    };

    TBSession.prototype.sessionDisconnectedHandler = function(event) {};

    TBSession.prototype.removeEventListener = function(event, handler) {
    	//TODO implement this (requires changes in addEventListener) 
//        var _this = this;
        console.warn("JS: Remove Event Listener Called NOT IMPLEMENTED YET!");
    };
    
    TBSession.prototype.addEventListener = function(event, handler) {
      var _this = this;
      console.log("JS: Add Event Listener Called");
      if (event === 'sessionConnected') {
        return this.sessionConnectedHandler = function(event) {
        	if(_logLevel <= _logLevels['DEBUG']){
        		_log('sessionConnectedHandler: '+JSON.stringify(event), _logLevels['DEBUG']);
        	}
          _this.connection = event.connection;
          
          //FIXME need to add capabilities (not yet impl. / available in Android implementation) --> "fake" capabilities obj.
          _this.capabilities = {
        	  forceDisconnect: 0,
        	  forceUnpublish: 0,
        	  publish: 1,
        	  subscribe: 1,
        	  supportsWebRTC: 1
          };
          
          //FIXME extend event-object (e.g. jquery-opentok assumes that an preventDefault-function exists...)
          if(!event.preventDefault){
        	  event.preventDefault = function dummyPreventDefault(){}; 
          }
          
          //FIXME MOD FIX: listener for receiving the corrected connection-information of the session ////////////
          if(!_this.connection || !_this.connection.connectionId){
              Cordova.exec(function(connection){
            	  	  _log("JS FIX.sessionConnectedHandler(): received corrected data for session.connection: "+JSON.stringify(connection), _logLevels['INFO']);
	            	  _this.connection = connection;

	            	  //if publisher already was inserted into page with the undefined ID, try to correct its ID:
	            	  var publisherElement = null;//document.getElementById('TBStreamConnection'+'undefined');
	            	  
	            	  var objects = document.getElementsByClassName('OT_root');
	            	  for (var _i = 0, _len = objects.length; _i < _len; _i++) {
	            	      var e = objects[_i];
	            	      var streamId = e.dataset.streamId;
	            	      if(streamId == 'undefined'){
	            	    	  publisherElement = e;
	            	    	  break;
	            	      }
	            	     
	            	    }
	            	  
	            	  if(publisherElement){
	            		  
	            		  
	            		  var newId = //"TBStreamConnection" + 
	            		  				_this.connection.connectionId;
	            		  
	            		  _log("JS FIX.sessionConnectedHandler(): correcting element ID for publisher DOM element, new ID: "+newId, _logLevels['INFO']);
		            	  
	            		  _this.publisher.obj.id = newId;
//	            		  publisherElement.id = newId;
	            		  publisherElement.setAttribute("data-streamid", streamId);
	            		  
	            	  }
	            	  
//	                  return handler(event);
	                  
	              }, TBError, "TokBox", "getSessionConnection", []
              );
          }
//          else 
          /////////////////////////////////////////
          
          return handler(event);
        };
      } 
      else if (event === 'streamDestroyed') {
        return this.streamDisconnectedHandler = function(response) {
          var arr, stream;
          _log("streamDestroyedHandler, data: '"+response+"'", _logLevels['DEBUG']);
          arr = response.split(' ');
          stream = {
            connection: {
              connectionId: arr[0]
            },
            streamId: arr[1]
          };
          
          if(_logLevel <= _logLevels['DEBUG']){
        	  _log("streamDestroyedHandler, streamId "+stream.streamId + ", connectionId "+stream.connection.connectionId, _logLevels['DEBUG']);
          }
          
          //TODO event needs "reason" property

          //FIXME extend event-object (e.g. jquery-opentok assumes that an preventDefault-function exists...)
          var event = {
            streams: [stream]
          };
          if(!event.preventDefault){
        	  var isPrevented = false;
        	  event.preventDefault = function preventDefaultImpl(){ isPrevented = true; };
        	  event.isDefaultPrevented = function isDefaultPreventedImpl(){ return isPrevented;};  
          }
//          return handler({
//            streams: [stream]
//          });
          var result = handler(event);
          
          //if event was not 'prevented'...
          if( typeof result === 'undefined' || result || !event.isDefaultPrevented()){
              _log("streamDestroyedHandler: executing default action (unpublish/unsubscibe)", _logLevels['DEBUG']);
        	  //REMOVE widget from page
        	  if(_this.publisher && _this.connection && _this.connection.connectionId == stream.connection.connectionId){
        		  
        		  //if this is caused by unpublish() 
        		  //  --> _this.isPublishing === false
        		  //  --> destroy publisher
        		  
        		  if(_this.isPublishing === false && _this.publisher){
            		  _this.publisher.destroy();
        		  }
        		  else {
            		  // otherwise: do nothing 
        			  // (-> publisher should already have been destroyed by native implementation, e.g. in case of sessionDisconnected)
        			  _this.isPublishing = false;
        		  }
        		  _this.publisher = null;  
        	  }
        	  else {
        		  _this.unsubscribe(stream);
        	  }
          }
        };
      } 
      else if (event === 'streamCreated') {
        this.streamCreatedHandler = function(response) {
          var arr, stream;
          arr = response.split(' ');
          stream = {
            connection: {
              connectionId: arr[0]
            },
            streamId: arr[1]
          };

          //FIXME extend event-object (e.g. jquery-opentok assumes that an preventDefault-function exists...)
          var event = {
            streams: [stream]
          };
          if(!event.preventDefault){
        	  event.preventDefault = function dummyPreventDefault(){}; 
          }
          //FIXME this is not in the spec, but the API doc references an event's target (the session?)
          event.target = _this;
//          return handler({
//            streams: [stream]
//          });
          return handler(event);
        };
        return Cordova.exec(this.streamCreatedHandler, TBError, "TokBox", "streamCreatedHandler", []);
      } 
      else if (event === 'sessionDisconnected') {
        return this.sessionDisconnectedHandler = function(event) {
          _log("sessionDisconnectedHandler, data "+event, _logLevels['DEBUG']);

          if(!event.preventDefault){
        	  var isPrevented = false;
        	  event.preventDefault = function preventDefaultImpl(){ isPrevented = true; };
        	  event.isDefaultPrevented = function isDefaultPreventedImpl(){ return isPrevented;};  
          }
          return handler(event);
        };
      } 
      else if (event === 'connectionCreated') {
    	  
    	this.sessionConnectionCreatedHandler = function(response) {

          //FIXME extend event-object (should be a ConnectionEvent)
          var event = {
//            reason: response.reason,
            connections: [response.connection]
          };
          if(!event.preventDefault){
        	  event.preventDefault = function dummyPreventDefault(){}; 
          }
          
          event.target = _this;
          return handler(event);
        };
        return Cordova.exec(this.sessionConnectionCreatedHandler, TBError, "TokBox", "sessionConnectionCreatedHandler", []);
      } 
      else if (event === 'connectionDestroyed') {
    	  
      	this.sessionConnectionDestroyedHandler = function(response) {

            //FIXME extend event-object (should be a ConnectionEvent)
            var event = {
              reason: response.reason,
              connections: [response.connection]
            };
            if(!event.preventDefault){
          	  event.preventDefault = function dummyPreventDefault(){}; 
            }
            
            event.target = _this;
            
            return handler(event);
          };
          return Cordova.exec(this.sessionConnectionDestroyedHandler, TBError, "TokBox", "sessionConnectionDestroyedHandler", []);
        }
      else {

          console.error("tried to register unknown event handler for event '"+event+"'");
          
    	  //TODO implement listener/callback for missing events: connectionCreated, connectionDestroyed
      }
    };

    TBSession.prototype.connect = function(apiKey, token, properties) {
      if (properties == null) {
        properties = {};
      }
      _log("JS: Connect Called", _logLevels['DEBUG']);
      this.apiKey = apiKey;
      this.token = token;
      
      Cordova.exec(this.sessionConnectedHandler, TBError, "TokBox", "connect", [this.apiKey, this.token]);
      Cordova.exec(this.streamDisconnectedHandler, TBError, "TokBox", "streamDisconnectedHandler", []);
      Cordova.exec(this.sessionDisconnectedHandler, TBError, "TokBox", "sessionDisconnectedHandler", []);
    };

    TBSession.prototype.disconnect = function() {
      return Cordova.exec(TBSuccess, TBError, "TokBox", "disconnect", []);
    };


    //FIXME cannot use "function overloading" in JavaScript, QUICK-FIX: merge both into one function... 
//    TBSession.prototype.publish = function(divName, properties) {
//      this.publisher = new TBPublisher(divName, properties, this);
//      return this.publisher;
//    };
//
//    TBSession.prototype.publish = function(publisher) {
//      var newId;
//      this.publisher = publisher;
//      newId = "TBStreamConnection" + this.connection.connectionId;
//      this.publisher.obj.id = newId;
//      return Cordova.exec(TBSuccess, TBError, "TokBox", "publish", []);
//    };
    
    TBSession.prototype.publish = function(divName, properties) {
    	var publisher;
    	if(arguments.length == 2){
	        this.publisher = new TBPublisher("", divName, properties, this);
//	        return this.publisher;
	        publisher = this.publisher;
    	}
    	else {
    		publisher = divName;
    		
//      //function(publisher) { //MOD: original signature for 1-argument-function-call
//            var newId;
            this.publisher = publisher;
//            newId = "TBStreamConnection" + this.connection.connectionId;
            if(typeof this.connection.connectionId === 'undefined' || this.connection.connectionId === 'undefined'){
            	_log('JS Session.publish(): UNDEFINED STREAM ID for PUBLISHER! stack-trace: '+(new ReferenceError()).stack, _logLevels['ERROR']);	
            }
//            this.publisher.obj.id = newId;
    	}
//        return 
        	Cordova.exec(TBSuccess, TBError, "TokBox", "publish", []);
        this.isPublishing = true;
        return this.publisher;
      };

    TBSession.prototype.unpublish = function() {
      var element;//, elementId;
      console.log("JS: Unpublish");
//      elementId = "TBStreamConnection" + this.connection.connectionId;
//      element = document.getElementById(elementId);
      element = this.publisher? this.publisher.obj : null;
      if (element) {
        element.parentNode.removeChild(element);
        //TODO delete reference this.publisher?
        TBUpdateObjects();
      }
      this.isPublishing = false;
      return Cordova.exec(TBSuccess, TBError, "TokBox", "unpublish", []);
    };

    TBSession.prototype.subscribe = function(one, two, three) {
      var domId, subscriber;
      if ((three != null)) {
        subscriber = new TBSubscriber(one, two, three);
        addSubscriberToList(subscriber);//FIXME MOD
        return subscriber;
      }
      if ((two != null)) {
        if (typeof two === "object") {
          domId = TBGenerateDomHelper();
          subscriber = new TBSubscriber(one, domId, two);
          addSubscriberToList(subscriber);//FIXME MOD
          return subscriber;
        } else {
          subscriber = new TBSubscriber(one, two, {});
          addSubscriberToList(subscriber);//FIXME MOD
          return subscriber;
        }
      }
      domId = TBGenerateDomHelper();
      subscriber = new TBSubscriber(one, domId, {});
      addSubscriberToList(subscriber);//FIXME MOD
      return subscriber;
    };

    TBSession.prototype.unsubscribe = function(subscriber) {
    	//FIXME MOD start
    	if(!subscriber){
    		unsubscribeAll();
    		return;///////// EARLY EXIT ///////////////
    	}
    	removeSubscriberFromList(subscriber);
    	//FIXME MOD end
    	
      var element, elementId;
      if(_logLevel <= _logLevels['DEBUG']){
    	  _log("JS: Unsubscribe"+(subscriber && subscriber.streamId? ", streamId " + subscriber.streamId : " INVALID STREAM ARGS"), _logLevels['DEBUG']);
      }
      elementId = subscriber.streamId;
//      element = document.getElementById("TBStreamConnection" + elementId);
      element = streamElements[elementId];
      if (element) {
        _log("unsubscribe: removing element for stream "+elementId, _logLevels['DEBUG']);
        element.parentNode.removeChild(element);
        delete streamElements[elementId];
        TBUpdateObjects();
      }
      
      return Cordova.exec(TBSuccess, TBError, "TokBox", "unsubscribe", [subscriber.streamId]);
    };

    TBSession.prototype.streamDisconnectedHandler = function(streamId) {
      var element;//, elementId;
      _log("JS: Stream Disconnected Handler Executed", _logLevels['DEBUG']);
//      elementId = "TBStreamConnection" + streamId;
//      element = document.getElementById(elementId);
      element = streamElements[streamId];
      if (element) {
        element.parentNode.removeChild(element);
        delete streamElements[streamId];
        TBUpdateObjects();
      }
    };
    
    //FIXME MOD start
    var _subscriberStreams = [];
    function addSubscriberToList(subscriber){
    	_subscriberStreams.push(subscriber.streamId);
    }
    function removeSubscriberFromList(subscriber){
    	for(var i=0, size = _subscriberStreams.length; i < size; ++i){
    		if(subscriber.streamId == _subscriberStreams[i]){
    			_subscriberStreams.splice(i,1);
    			return;
    		}
    	}
    }
    function unsubscribeAll(){
    	var streams = _subscriberStreams;
    	_subscriberStreams = [];
    	
    	for(var i=0, size = streams.length; i < size; ++i){
    		Cordova.exec(TBSuccess, TBError, "TokBox", "unsubscribe", [ streams[i] ]);
    	}
    }
    //FIXME MOD end

    return TBSession;

  })();

  TBSubscriber = function(stream, divName, properties) {
    var height, name, obj, position, subscribeToVideo, width, zIndex, _ref, _ref1, _ref2;
    
        
    if(_logLevel <= _logLevels['DEBUG']){

    	_log("JS: Subscribing",_logLevels['DEBUG']);
    	
        var streamDebugStr = stream;
        try{
        	streamDebugStr = (JSON ? JSON.stringify(this.stream): stream)
        } catch(e){
        	console.log('JS DEBUG TBSubscriber.constructor(): could not stringify stream-argument...');
        }
        
    	_log('JS TBSubscriber.constructor(): stream '+
    			streamDebugStr+',  divName '+
    			divName+', properties '+
    			(JSON ? JSON.stringify(this.properties): this.properties.toString())+', stack-trace: '
    			+(new ReferenceError()).stack,
    		_logLevels['DEBUG']
    	);
    }
    
    this.streamId = stream.streamId;
    this.stream = stream;//FIXME MOD added stream property
    width = 160;
    height = 120;
    subscribeToVideo = "true";
    zIndex = TBGetZIndex(document.getElementById(divName));
    var isDefaultWidth = true, isDefaultHeight = true;//FIXME MOD
    if ((properties != null)) {
      width = (_ref = properties.width) != null ? _ref : 160;
      height = (_ref1 = properties.height) != null ? _ref1 : 120;
      name = (_ref2 = properties.name) != null ? _ref2 : "";
      if ((properties.subscribeToVideo != null) && properties.subscribeToVideo === false) {
        subscribeToVideo = "false";
      }
      
      isDefaultWidth  = _ref  == null;
      isDefaultHeight = _ref1 == null;
    }
    
    if(_logLevel <= _logLevels['DEBUG']){
    	_log('JS TBSubscriber.constructor(): creating DOM element with -> width '
    			+width+', height '
    			+height+', name '
    			+name+',  divName '
    			+divName+', subscribeToVideo '
    			+subscribeToVideo,
    		_logLevels['DEBUG']
    	);	
    }
    
    obj = replaceWithObject(divName, stream.streamId, {
      width: width,
      height: height,
      isDefaultWidth: isDefaultWidth,
      isDefaultHeight: isDefaultHeight
    });
    if( ! obj){
  	  _log('OpenTokPlugin.TBSubscriber(): Could not create DIV for Subscriber', _logLevels['WARN']);
  	  return;///////////////// EARLY EXIT ///////////////////////
    }
    position = getPosition(obj.id);
    return Cordova.exec(TBSuccess, TBError, "TokBox", "subscribe", [stream.streamId, position.top, position.left, width, height, subscribeToVideo, zIndex]);
  };

}).call(this);
