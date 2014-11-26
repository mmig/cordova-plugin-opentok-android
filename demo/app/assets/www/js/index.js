var app = {
		initialize: function() {
			this.bind();
		},
		bind: function() {
			document.addEventListener('deviceready', this.deviceready, false);
		},
		deviceready: function() {
//			var apiKey 		// Replace with your apiKey.
//			var sessionId	// Replace with your own session ID. Make sure it matches helloWorld.html
//			var token		// for generating Session IDs and Tokens, See https://dashboard.tokbox.com/projects
			var apiKey, session, sessionId, token;
			apiKey = "xxxxxxxx"
			session = "1_MXxxxxxxxxxxxxx-XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX-xxx";
			token = "X1==xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx==";

			sessionId = session;
			
			var session = TB.initSession(sessionId); 
			var publisher = TB.initPublisher( apiKey, "myPublisherDiv" ); // Replace with your API key

			TB.addEventListener("exception", errorHandler);
			
			session.addEventListener("sessionConnected", sessionConnectedHandler);
			session.addEventListener("streamCreated", streamCreatedHandler);
			session.connect(apiKey, token);

			function sessionConnectedHandler(event) {
				console.error('CordovaTB: sessionConnectedHandler '+JSON.stringify(event.streams));
				subscribeToStreams(event.streams);
				session.publish( publisher );
			}

			function streamCreatedHandler(event) {
				console.error('CordovaTB: streamCreatedHandler '+JSON.stringify(event.streams));
				subscribeToStreams(event.streams);
			}
			
			function errorHandler(event){
				console.error('CordovaTB: errorHandler '+JSON.stringify(event));
			}

			function subscribeToStreams(streams) {
				for (i = 0; i < streams.length; i++) {
					var stream = streams[i];

					var msg = 'CordovaTB: subscribeToStream no. '+i+': '+stream.connection.connectionId+' \t (session.connection.connectionId: '+session.connection.connectionId+')';

					if (stream.connection.connectionId != session.connection.connectionId) {
						var div = document.createElement('div');
						div.setAttribute('id', 'stream' + stream.streamId);
						document.body.appendChild(div);
						session.subscribe(stream, div.id);

						console.log(msg);
					}
					else {
						console.error('IGNORED - '+ msg);
					}
				}
			}
			
//			document.addEventListener("pause", onPause, false);
//			document.addEventListener("resume", onResume, false);
//			var wasPaused = false;
//			function onPause(){
//				wasPaused = true;
//				if(session){
//					session.unpublish();
//				}
//			}
//			
//			function onResume(){
//				if(wasPaused === true){
//					wasPaused = false;
//					if(session){
//						session.publish();
//					}
//				}
//			}
		},
		report: function(id) { 
			console.log("report:" + id);
			// hide the .pending <p> and show the .complete <p>
			document.querySelector('#' + id + ' .pending').className += ' hide';
			var completeElem = document.querySelector('#' + id + ' .complete');
			completeElem.className = completeElem.className.split('hide').join('');
		}
};


