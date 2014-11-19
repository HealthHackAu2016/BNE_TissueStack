/*
 * This file is part of TissueStack.
 *
 * TissueStack is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * TissueStack is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with TissueStack.  If not, see <http://www.gnu.org/licenses/>.
 */
TissueStack = {
	requiredLibs : [
	                	"/js/libs/sylvester/sylvester.js",
	                	"/js/TissueStack.js",
	                	"/js/TissueStack.MouseWheel.js",
	                	"/js/TissueStack.Utils.js",
	                	"/js/TissueStack.Extent.js",
	                	"/js/TissueStack.Queue.js",
	                	"/js/TissueStack.Canvas.js",
	                	"/js/TissueStack.Events.js",
	                	"/js/TissueStack.DataSetStore.js",
                        "/js/TissueStack.ComponentFactory.js"
	                ]
};

TissueStack.Embedded = function (div, server, data_set_id, include_cross_hair, use_image_service, initOpts) {
	// evaluate input and conduct peliminary checks
	
	if (typeof(div) != 'string' || $.trim(div) == '') {
		alert("Empty div ids are invalid!");
		return;
	}
	
	this.div = $("#" + div);
	if (this.div.length == 0) {
		alert("Given div id does not exist!");
		return;
	}
	
	if (typeof(server) != 'string' || $.trim(server) == '') {
		this.writeErrorMessageIntoDiv("Empty server name was given!");
		return;
	}
	this.domain = this.extractHostNameFromUrl(server);

	if (typeof(data_set_id) != 'number' || data_set_id <= 0) {
		this.writeErrorMessageIntoDiv("Data Set id has to be greater than 0!");
		return;
	}
	this.data_set_id = data_set_id;

	if (typeof(include_cross_hair) != 'boolean' || include_cross_hair == true) {
		this.include_cross_hair = true;
	} else {
		this.include_cross_hair = false;
	}

	if (typeof(use_image_service) == 'boolean' && use_image_service == true) {
		this.use_image_service = true;
	} else {
		this.use_image_service = false;
	}

	if (typeof(initOpts) == 'object') {
		this.initOpts = initOpts;
	} else {
		this.initOpts = null;
	}
	
	// check canvas support
	if (!this.supportsCanvas()) {
		this.writeErrorMessageIntoDiv("Sorry, your browser does not support the HTML 5 feature canvas");
		return;
	}
	
	var afterLoadActions = function(_this) {
        // load server configuration values needed
        _this.loadDataBaseConfiguration();
        // load given data set configuration
        _this.loadDataSetConfigurationFromServer();
        // this is for when the window dimensions & the div dimensions may change dynamically
        _this.registerWindowResizeEvent();
	};
	
	// include the java script and css that is needed for the rest of the functionality
	this.includeJavaScriptAndCssNeeded(afterLoadActions);
};

TissueStack.Embedded.prototype = {
	librariesLoaded : 0,
	getDiv : function() {
		return this.div;
	},
	writeErrorMessageIntoDiv : function(message) {
		this.getDiv().html(message);
	},
	supportsCanvas : function() {
	  var elem = document.createElement('canvas');
	  return !!(elem.getContext && elem.getContext('2d'));
	},
	extractHostNameFromUrl : function(url) {
		if (typeof(url) != "string") {
			return null;
		}

		// trim whitespace
		url = $.trim(url);

		// take off protocol 
		if (url.indexOf("http://") == 0 || url.indexOf("file://") == 0) {
			url = url.substring(7, url.length);
		} else if (url.indexOf("https://") == 0 || url.indexOf("file:///") == 0) {
			url = url.substring(8, url.length);
		} else if (url.indexOf("ftp://") == 0) {
			url = url.substring(6, url.length);
		}
		// strip it off a potential www
		if (url.indexOf("www.") == 0) {
			url = url.substring(4, url.length);
		}
			
		//now cut off anything after the initial '/' if exists to preserve the domain
		var slashPosition = url.indexOf("/");
		if (slashPosition > 0) {
			url = url.substring(0, slashPosition);
		}

		return url;
	},	
	includeJavaScriptAndCssNeeded : function(afterLoadActions) {
		// if header does not exist => create it
		var head = $("head");
		if (head.length == 0) {
			head = $("html").append("<head></head>");
		}

		// add all the css that's necessary
		head.append(this.createElement("link", "http://" + this.domain + "/css/default.css"));

		_this = this;
		
		var checkLoadOfLibraries = function() {
			_this.librariesLoaded++;
			if (_this.librariesLoaded == TissueStack.requiredLibs.length) {
				afterLoadActions(_this);
			}
		};
		
		var error = function() {
			alert("Failed to load required library!");
		};
		
		for (var i=0;i<TissueStack.requiredLibs.length;i++) {
			(function () {
				$.ajax({
					url : "http://" + _this.domain + TissueStack.requiredLibs[i],
					async: false,
					dataType : "script",
					cache : false,
					timeout : 30000,
					success : checkLoadOfLibraries,
					error: error
				});
			})();
		}
	},
	createElement : function(tag, value) {
		if (typeof(tag) != "string" || typeof(value) != "string" || tag.length == 0 || value.length == 0) {
			return null;
		}
		
		var newEl = document.createElement(tag);
		if (tag == "script") {
			newEl.src = value;
		} else if (tag == "link") {
			newEl.rel = "stylesheet";
			newEl.href = value;
		} else {
			newEl.value = value;
		}

		return newEl;
	}, applyUserParameters : function(dataSet) {
		if (!this.initOpts) return;
		
		var plane_id = typeof(this.initOpts['plane']) === 'string' ?  this.initOpts['plane'] : 'y';

		// plane or data set does not exist => good bye
		if (!dataSet || !dataSet.planes[plane_id]) return;
		
		// do we need to swap planes in the main view
		var maximizeIcon = $("#dataset_1 .canvas_" + plane_id + ",maximize_view_icon");
		if (maximizeIcon && maximizeIcon.length == 1) {
			// not elegant but fire event to achieve our plane swap
			maximizeIcon.click();
		}
		
		var _this = this;
		var plane = dataSet.planes[plane_id];
		
		if (_this.initOpts['zoom'] != null && _this.initOpts['zoom'] >= 0 && _this.initOpts['zoom'] < plane.data_extent.zoom_levels.length) {
			plane.changeToZoomLevel(_this.initOpts['zoom']); 
		}

		if (_this.initOpts['color'] && _this.initOpts['color'] != 'grey') {
			// change color map collectively for all planes
			for (var id in dataSet.planes) {
				dataSet.planes[id].color_map = _this.initOpts['color'];
				dataSet.planes[id].is_color_map_tiled = null;
			}
		}

		var givenCoords = {};
		if (_this.initOpts['x'] != null || _this.initOpts['y'] != null || _this.initOpts['z'] != null) {
			givenCoords = {x: _this.initOpts['x'] != null ? _this.initOpts['x'] : 0,
					y: _this.initOpts['y'] != null ? _this.initOpts['y'] : 0,
					z: _this.initOpts['z'] != null ? _this.initOpts['z'] : 0};
			
			if (plane.getDataExtent().worldCoordinatesTransformationMatrix) {
				givenCoords = plane.getDataExtent().getPixelForWorldCoordinates(givenCoords);
			}
		} else {
			givenCoords = plane.getRelativeCrossCoordinates();
			givenCoords.z = plane.getDataExtent().slice;
		}
		
		var now = new Date().getTime();
		plane.events.changeSliceForPlane(givenCoords.z);
		plane.queue.drawLowResolutionPreview(now);
		plane.queue.drawRequestAfterLowResolutionPreview(null, now);
	},
	loadDataSetConfigurationFromServer : function() {
		var _this = this;
		var url = "http://" + _this.domain + "/" + 
			TissueStack.configuration['server_proxy_path'].value + "/?service=services&sub_service=data&action=all&id=" 
			+ _this.data_set_id + "&include_planes=true";
		
		// create a new data store
		TissueStack.dataSetStore = new TissueStack.DataSetStore(null, true);
		
		TissueStack.Utils.sendAjaxRequest(
			url, 'GET',	true,	
			function(data, textStatus, jqXHR) {
				if (!data.response && !data.error) {
					_this.writeErrorMessageIntoDiv("Did not receive anyting from backend, neither success nor error ....");
					return;
				}
				
				if (data.error) {
					var message = "Application Error: " + (data.error.description ? data.error.description : " no more info available. check logs.");
					_this.writeErrorMessageIntoDiv(message);
					return;
				}
				
				if (data.response.noResults) {
					_this.writeErrorMessageIntoDiv("No data set found in configuration database for given id");
					return;
				}
				
				var dataSet = data.response[0];
				
				// create a new data store
				TissueStack.dataSetStore = new TissueStack.DataSetStore(null, true);
				// add to data store
				dataSet = TissueStack.dataSetStore.addDataSetToStore(dataSet, _this.domain);
				
				if (!dataSet.data || dataSet.data.length == 0) {
					this.writeErrorMessageIntoDiv("Data set '" + dataSet.id + "' does not have any planes associated with it!");
					return; 
				}

				// create the HTML necessary for display and initialize the canvas objects
                TissueStack.ComponentFactory.createDataSetWidget(
                    _this.getDiv().attr("id"), dataSet, 1, _this.domain, _this.include_cross_hair, true, _this.use_image_service);
				if (_this.initOpts) _this.applyUserParameters(dataSet);
                
                /*
                // add to data store
                dataSet = data.response[1];
                dataSet = TissueStack.dataSetStore.addDataSetToStore(dataSet, _this.domain);
                TissueStack.ComponentFactory.createDataSetWidget(
                    _this.getDiv().attr("id") + "2", dataSet, 2, _this.domain, _this.include_cross_hair, true, _this.use_image_service);
                */
			},
			function(jqXHR, textStatus, errorThrown) {
				_this.writeErrorMessageIntoDiv("Error connecting to backend: " + textStatus + " " + errorThrown);
			}
		);
	},
	loadDataBaseConfiguration : function() {
		// we do this one synchronously
		TissueStack.Utils.sendAjaxRequest(
			"http://" + this.domain + "/" + TissueStack.configuration['server_proxy_path'].value +
				"/?service=services&sub_service=configuration&action=all", 'GET', false,
			function(data, textStatus, jqXHR) {
				if (!data.response && !data.error) {
					alert("Did not receive anyting, neither success nor error ....");
					return;
				}
				
				if (data.error) {
					var message = "Application Error: " + (data.error.description ? data.error.description : " no more info available. check logs.");
					alert(message);
					return;
				}
				
				if (data.response.noResults) {
					alert("No configuration info found in database");
					return;
				}
				var configuration = data.response;
				
				for (var x=0;x<configuration.length;x++) {
					if (!configuration[x] || !configuration[x].name || $.trim(!configuration[x].name.length) == 0) {
						continue;
					}
					TissueStack.configuration[configuration[x].name] = {};
					TissueStack.configuration[configuration[x].name].value = configuration[x].value;
					TissueStack.configuration[configuration[x].name].description = configuration[x].description ? configuration[x].description : "";
				};
				
				TissueStack.Utils.loadColorMaps();
			},
			function(jqXHR, textStatus, errorThrown) {
				alert("Error connecting to backend: " + textStatus + " " + errorThrown);
			}
		);
	},
	registerWindowResizeEvent : function() {
		var _this = this;
		// handle dynamic window & parent div resizing
		$(window).resize(function() {
			_this.adjustCanvasSizes();
			
			// set new canvas dimensions
			var dataSet = TissueStack.dataSetStore.getDataSetByIndex(0);
			for (var plane in dataSet.planes) {
				dataSet.planes[plane].resizeCanvas();
			}
		});
	},
};