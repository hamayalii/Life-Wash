/**
 * Order Form Location Picker Feature (Phase B1)
 * Optional GPS & Interactive Map Picker for public order form
 */
(function () {
  "use strict";

  // Kurdish Sorani UI Strings
  const STR = {
    chooseMapLabel: "دیاریکردن لەسەر نەخشە",
    chooseMapSelected: "ناونیشانەکە دیاری کرا",
    mapTitle: "نەخشەی شوێن",
    myLocation: "شوێنی من",
    close: "داخستن",
    loading: "چاوەڕێبە، شوێنەکەت دیاری دەکرێت...",
    privacy: "شوێنکەوتن تەنها دوای کرتەکردن داواکراوە. داتاکان تەنها لەکاتی ناردنی داواکارییەکەت پاشەکەوت دەکرێن.",
    osmNote: "نەخشەکە لە OpenStreetMap وەرگیراوە.",
    hintMove: "مارکەرەکە ڕابکێشە یان لەسەر نەخشەکە کرتە بکە بۆ ڕاستکردنەوەی شوێن.",
    error_permission_denied: "ڕێگەپێدان بە شوێنکەوتن ڕەتکرایەوە. ئەتوانیت شوێنەکە بە دەستی لەسەر نەخشەکە دیاری بکەیت.",
    error_unavailable: "شوێنکەوتن بەردەست نییە. تکایە شوێنەکە بە دەستی دیاری بکە.",
    error_timeout: "کاتی گەیشت. تکایە دووبارە هەوڵبدە.",
    error_unsupported: "وێبگەڕەکەت پشتگیری شوێنکەوتن ناکات.",
    error_insecure: "شوێنکەوتن تەنها لە پەیوەندی پارێزراو (HTTPS) کاردەکات. شوێنەکە بە دەستی دیاری بکە.",
    error_map_load: "نەخشەکە بار نەبوو. تکایە دووبارە هەوڵبدە یان ناونیشانەکە بنووسە.",
    warning_in_app_browser: "هەندێک وێبگەڕی ناوەخۆیی شوێنکەوتن دەگرن. تکایە وێبگەڕەکە بەکاربێنە.",
    warning_outside_bounds: "شوێنەکە دەرەوەی هەرێمی کوردستانە. دڵنیایت؟",
    warning_gps_approximate: "شوێنەکە نزیکەییە و وردییەکەی کەمە. دەتوانیت مارکەرەکە ڕابکێشیت بۆ ڕاستکردنەوەی شوێنەکە.",
    saved: "شوێن دیاری کرا.",
    addrAutofilled: "ناونیشان لە نەخشەکەوە وەرگیرا، ئەتوانیت بیگۆڕیت.",
    useSuggested: "بەکاربهێنانی ناونیشانی پێشنیارکراو",
    dismiss: "نەخێر"
  };

  // Default map center (Sulaymaniyah city center) — single configurable constant
  const DEFAULT_CENTER = { lat: 35.56, lng: 45.43, zoom: 12 };
  const GPS_ZOOM = 17;

  // Kurdistan Region bounds (approx lat 34.5-37.4, lng 42.3-46.3)
  function isWithinKurdistan(lat, lng) {
    return lat >= 34.5 && lat <= 37.4 && lng >= 42.3 && lng <= 46.3;
  }

  // In-memory state only (no localStorage / sessionStorage)
  const state = {
    latitude: null,
    longitude: null,
    accuracyMeters: null,
    locationSource: null, // "gps" | "manual"
    locationCapturedAt: null,
    formattedAddress: null
  };

  let addressTouchedByUser = false;
  let addressAutoFilled = false;
  let geocodeDebounceTimer = null;
  let geocodeAbortController = null;
  let rateLimitUntil = 0;
  let currentRequestId = 0;

  // DOM Elements
  const chooseMapBtn = document.getElementById("chooseMapBtn");
  const mapPanel = document.getElementById("mapPanel");
  const mapTitle = document.getElementById("mapTitle");
  const mapEl = document.getElementById("mapEl");
  const myLocBtn = document.getElementById("myLocBtn");
  const closeMapBtn = document.getElementById("closeMapBtn");
  const locStatus = document.getElementById("locStatus");

  const addressInput = document.getElementById("address");
  const addrAutofillStatus = document.getElementById("addrAutofillStatus");
  const addrSuggestWrapper = document.getElementById("addrSuggestWrapper");
  const addrSuggestBtn = document.getElementById("addrSuggestBtn");
  const addrSuggestText = document.getElementById("addrSuggestText");
  const addrDismissBtn = document.getElementById("addrDismissBtn");

  // Map & Leaflet state
  let mapInstance = null;
  let markerInstance = null;
  let accuracyCircleInstance = null;
  let leafletLoadPromise = null;
  let isGeolocating = false;
  let lastFocusedElement = null;

  function setStatus(message, type) {
    if (!locStatus) return;
    locStatus.textContent = message || "";
    locStatus.className = "ol-status" + (type ? " ol-status-" + type : "");
  }

  /**
   * Lazy load Leaflet 1.9.4 script single-flight
   */
  function loadLeaflet() {
    if (window.L) {
      return Promise.resolve(window.L);
    }
    if (leafletLoadPromise) {
      return leafletLoadPromise;
    }

    leafletLoadPromise = new Promise(function (resolve, reject) {
      const script = document.createElement("script");
      script.src = "/assets/vendor/leaflet-1.9.4/leaflet.js";
      script.async = true;

      let timer = setTimeout(function () {
        cleanup();
        setStatus(STR.error_map_load, "error");
        reject(new Error("Leaflet script load timeout"));
      }, 8000);

      function cleanup() {
        if (timer) {
          clearTimeout(timer);
          timer = null;
        }
        script.onload = null;
        script.onerror = null;
        if (script.parentNode) {
          script.parentNode.removeChild(script);
        }
        leafletLoadPromise = null; // Reset so retry is possible
      }

      script.onload = function () {
        if (timer) {
          clearTimeout(timer);
          timer = null;
        }
        if (window.L) {
          window.L.Icon.Default.imagePath = "/assets/vendor/leaflet-1.9.4/images/";
          resolve(window.L);
        } else {
          cleanup();
          setStatus(STR.error_map_load, "error");
          reject(new Error("Leaflet L object not found"));
        }
      };

      script.onerror = function () {
        cleanup();
        setStatus(STR.error_map_load, "error");
        reject(new Error("Leaflet script failed to load"));
      };

      document.head.appendChild(script);
    });

    return leafletLoadPromise;
  }

  /**
   * Open the map panel with accessibility focus management
   */
  function openMapPanel(triggerElement) {
    if (!mapPanel) return;
    lastFocusedElement = triggerElement || document.activeElement;
    mapPanel.hidden = false;
    mapPanel.classList.remove("hidden");

    if (mapTitle) {
      mapTitle.setAttribute("tabindex", "-1");
      mapTitle.focus();
    }
  }

  /**
   * Close the map panel and return focus
   */
  function closeMapPanel() {
    if (!mapPanel || mapPanel.hidden) return;
    mapPanel.hidden = true;
    mapPanel.classList.add("hidden");

    if (lastFocusedElement && typeof lastFocusedElement.focus === "function") {
      lastFocusedElement.focus();
    }
  }

  /**
   * Ensure map is initialized and centered
   */
  function initOrUpdateMap(targetLat, targetLng, zoomLevel, hasGpsFix) {
    return loadLeaflet().then(function (L) {
      if (!mapEl) return;

      const lat = targetLat != null ? targetLat : DEFAULT_CENTER.lat;
      const lng = targetLng != null ? targetLng : DEFAULT_CENTER.lng;
      const zoom = zoomLevel != null ? zoomLevel : DEFAULT_CENTER.zoom;

      if (!mapInstance) {
        mapInstance = L.map(mapEl, {
          center: [lat, lng],
          zoom: zoom,
          scrollWheelZoom: false, // Disabled initially to avoid scroll hijacking
          attributionControl: true
        });

        // OpenStreetMap Tile Layer
        L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
          attribution: "&copy; OpenStreetMap contributors",
          maxZoom: 19
        }).addTo(mapInstance);

        // Scroll wheel zoom enable on map focus/click, disable on blur
        mapInstance.on("focus", function () {
          mapInstance.scrollWheelZoom.enable();
        });
        mapInstance.on("blur", function () {
          mapInstance.scrollWheelZoom.disable();
        });
        mapEl.addEventListener("click", function () {
          mapInstance.scrollWheelZoom.enable();
        });

        // Mobile touch scroll protection:
        // Disable touch dragging until explicit user tap/focus on the map container
        const isTouch = L.Browser && L.Browser.touch;
        if (isTouch) {
          mapInstance.dragging.disable();
          mapEl.addEventListener("click", function () {
            mapInstance.dragging.enable();
          });
          mapInstance.on("blur", function () {
            mapInstance.dragging.disable();
          });
        }

        // Click on map to place/move marker manually
        mapInstance.on("click", function (e) {
          onUserSelectedLocation(e.latlng.lat, e.latlng.lng, "manual", null);
        });
      } else {
        mapInstance.setView([lat, lng], zoom);
        setTimeout(function () {
          mapInstance.invalidateSize();
        }, 50);
      }

      if (targetLat != null && targetLng != null) {
        updateMarkerAndCircle(targetLat, targetLng, hasGpsFix ? state.accuracyMeters : null);
      }

      return mapInstance;
    });
  }

  /**
   * Place or update marker and accuracy circle
   */
  function updateMarkerAndCircle(lat, lng, accuracyMeters) {
    if (!window.L || !mapInstance) return;
    const L = window.L;

    // Draggable marker with keyboard support
    if (!markerInstance) {
      markerInstance = L.marker([lat, lng], {
        draggable: true,
        keyboard: true
      }).addTo(mapInstance);

      markerInstance.on("dragend", function () {
        const pos = markerInstance.getLatLng();
        onUserSelectedLocation(pos.lat, pos.lng, "manual", null);
      });
    } else {
      markerInstance.setLatLng([lat, lng]);
    }

    // Accuracy circle (only for GPS)
    if (accuracyMeters && isFinite(accuracyMeters)) {
      if (!accuracyCircleInstance) {
        accuracyCircleInstance = L.circle([lat, lng], {
          radius: accuracyMeters,
          color: "#3b82f6",
          fillColor: "#60a5fa",
          fillOpacity: 0.15,
          weight: 1
        }).addTo(mapInstance);
      } else {
        accuracyCircleInstance.setLatLng([lat, lng]);
        accuracyCircleInstance.setRadius(accuracyMeters);
      }
    } else if (accuracyCircleInstance) {
      mapInstance.removeLayer(accuracyCircleInstance);
      accuracyCircleInstance = null;
    }
  }

  /**
   * Handle user location selection (either GPS fix or manual marker placement/drag)
   */
  function onUserSelectedLocation(lat, lng, source, accuracy) {
    const newLat = Number(lat.toFixed(6));
    const newLng = Number(lng.toFixed(6));

    // Snapshot previous selection so an outside-bounds choice can be undone
    const prev = {
      latitude: state.latitude,
      longitude: state.longitude,
      accuracyMeters: state.accuracyMeters,
      locationSource: state.locationSource,
      locationCapturedAt: state.locationCapturedAt
    };

    function apply() {
      state.latitude = newLat;
      state.longitude = newLng;
      state.locationSource = source; // "gps" | "manual"
      state.locationCapturedAt = new Date().toISOString();

      if (source === "gps" && accuracy != null && isFinite(accuracy)) {
        state.accuracyMeters = Number(accuracy);
      } else {
        state.accuracyMeters = null;
      }

      updateMarkerAndCircle(state.latitude, state.longitude, state.accuracyMeters);
      triggerReverseGeocode(state.latitude, state.longitude);
      updateChooseMapButtonState(true);
    }

    function revert() {
      state.latitude = prev.latitude;
      state.longitude = prev.longitude;
      state.accuracyMeters = prev.accuracyMeters;
      state.locationSource = prev.locationSource;
      state.locationCapturedAt = prev.locationCapturedAt;

      // Invalidate any in-flight/pending reverse geocode from the rejected point
      currentRequestId++;
      if (geocodeDebounceTimer) {
        clearTimeout(geocodeDebounceTimer);
        geocodeDebounceTimer = null;
      }
      if (geocodeAbortController) {
        geocodeAbortController.abort();
        geocodeAbortController = null;
      }

      if (prev.latitude != null && prev.longitude != null) {
        updateMarkerAndCircle(prev.latitude, prev.longitude, prev.accuracyMeters);
        updateChooseMapButtonState(true);
      } else {
        if (markerInstance && mapInstance) {
          mapInstance.removeLayer(markerInstance);
          markerInstance = null;
        }
        if (accuracyCircleInstance && mapInstance) {
          mapInstance.removeLayer(accuracyCircleInstance);
          accuracyCircleInstance = null;
        }
        updateChooseMapButtonState(false);
      }
      setStatus("", "");
    }

    apply();

    if (!isWithinKurdistan(newLat, newLng)) {
      setStatus(STR.warning_outside_bounds, "warn");
      AppDialog.confirm({
        title: STR.mapTitle,
        message: STR.warning_outside_bounds,
        confirmText: "دڵنیام",
        cancelText: "پاشگەزبوونەوە"
      }).then(function (ok) {
        if (ok) {
          setStatus(STR.saved, "success");
        } else {
          revert();
        }
      });
      return;
    }

    setStatus(STR.saved, "success");
  }

  /**
   * Update chooseMapBtn to reflect selected state
   */
  function updateChooseMapButtonState(isSelected) {
    if (!chooseMapBtn) return;

    const textSpan = chooseMapBtn.querySelector("span");
    if (isSelected) {
      chooseMapBtn.classList.add("ol-choose-map-btn-selected");
      if (textSpan) {
        textSpan.textContent = STR.chooseMapSelected;
      }
    } else {
      chooseMapBtn.classList.remove("ol-choose-map-btn-selected");
      if (textSpan) {
        textSpan.textContent = STR.chooseMapLabel;
      }
    }
  }

  /**
   * Reverse geocoding UI helpers and fetch logic
   */
  function showSuggestChip(suggestion) {
    const wrapper = document.getElementById("addrSuggestWrapper");
    const btn = document.getElementById("addrSuggestBtn");
    const textEl = document.getElementById("addrSuggestText");
    const dismiss = document.getElementById("addrDismissBtn");
    if (!wrapper || !btn) return;

    if (textEl) {
      textEl.textContent = suggestion;
    } else {
      btn.textContent = suggestion;
    }
    btn.setAttribute("title", STR.useSuggested);
    btn.setAttribute("aria-label", STR.useSuggested + ": " + suggestion);
    if (dismiss) {
      dismiss.setAttribute("title", STR.dismiss);
      dismiss.setAttribute("aria-label", STR.dismiss);
    }
    wrapper.classList.remove("hidden");
  }

  function hideSuggestChip() {
    const wrapper = document.getElementById("addrSuggestWrapper");
    if (wrapper) {
      wrapper.classList.add("hidden");
    }
  }

  function showAutofillStatus() {
    const statusEl = document.getElementById("addrAutofillStatus");
    if (statusEl) {
      statusEl.textContent = STR.addrAutofilled;
      statusEl.classList.remove("hidden");
    }
  }

  function hideAutofillStatus() {
    const statusEl = document.getElementById("addrAutofillStatus");
    if (statusEl) {
      statusEl.classList.add("hidden");
      statusEl.textContent = "";
    }
  }

  function handleGeocodeSuggestion(suggestion) {
    const addrInput = document.getElementById("address");
    if (!addrInput) return;

    if (!addressTouchedByUser && !addrInput.value.trim()) {
      // Input empty AND not touched -> fill #address automatically
      addrInput.value = suggestion;
      addressAutoFilled = true;
      showAutofillStatus();
      hideSuggestChip();
    } else if (addressAutoFilled && !addressTouchedByUser) {
      // While addressAutoFilled is true and user has not typed, replace
      addrInput.value = suggestion;
      showAutofillStatus();
      hideSuggestChip();
    } else if (addressTouchedByUser) {
      // Once user typed in #address, never overwrite; show chip button
      hideAutofillStatus();
      showSuggestChip(suggestion);
    }
  }

  function triggerReverseGeocode(lat, lng) {
    if (geocodeDebounceTimer) {
      clearTimeout(geocodeDebounceTimer);
      geocodeDebounceTimer = null;
    }
    if (geocodeAbortController) {
      geocodeAbortController.abort();
      geocodeAbortController = null;
    }

    const thisRequestId = ++currentRequestId;

    geocodeDebounceTimer = setTimeout(function () {
      if (Date.now() < rateLimitUntil) {
        return; // In 429 Retry-After cooldown
      }

      if (typeof fetch !== "function") return;

      const controller = typeof AbortController === "function" ? new AbortController() : null;
      geocodeAbortController = controller;

      fetch("/api/v1/geocode/reverse?lat=" + encodeURIComponent(lat) + "&lng=" + encodeURIComponent(lng), {
        signal: controller ? controller.signal : undefined
      })
        .then(function (res) {
          if (res.status === 429) {
            const retryHeader = res.headers && typeof res.headers.get === "function" ? res.headers.get("Retry-After") : null;
            const retrySec = retryHeader ? parseInt(retryHeader, 10) : 5;
            rateLimitUntil = Date.now() + (Number.isFinite(retrySec) ? retrySec * 1000 : 5000);
            return null;
          }
          if (!res.ok) {
            return null;
          }
          return res.json();
        })
        .then(function (data) {
          if (thisRequestId !== currentRequestId) {
            return; // Stale response ignored
          }
          if (!data || !data.address || typeof data.address !== "string") {
            return; // address: null or empty
          }
          const rawSuggestion = data.address.trim();
          if (!rawSuggestion) return;
          const suggestion = rawSuggestion.slice(0, 200);

          state.formattedAddress = suggestion;
          handleGeocodeSuggestion(suggestion);
        })
        .catch(function () {
          // Abort or error: show nothing, let user type
        });
    }, 600);
  }

  /**
   * Request browser geolocation
   */
  function requestGeolocation(triggerBtn) {
    if (isGeolocating) return;

    // In-app browser detection (best-effort UA sniffing)
    const isInApp = /FBAN|FBAV|Instagram|Telegram|WhatsApp|Line\/|; wv\)/i.test(navigator.userAgent || "");
    if (isInApp) {
      setStatus(STR.warning_in_app_browser, "warn");
    }

    // Check secure context
    if (window.isSecureContext === false) {
      setStatus(STR.error_insecure, "error");
      AppDialog.alert({ title: STR.mapTitle, message: STR.error_insecure, type: "error" });
      openMapPanel(triggerBtn);
      initOrUpdateMap(state.latitude, state.longitude, state.latitude != null ? GPS_ZOOM : DEFAULT_CENTER.zoom, false);
      return;
    }

    // Check geolocation API support
    if (!("geolocation" in navigator)) {
      setStatus(STR.error_unsupported, "error");
      AppDialog.alert({ title: STR.mapTitle, message: STR.error_unsupported, type: "error" });
      openMapPanel(triggerBtn);
      initOrUpdateMap(state.latitude, state.longitude, state.latitude != null ? GPS_ZOOM : DEFAULT_CENTER.zoom, false);
      return;
    }

    isGeolocating = true;
    if (triggerBtn) {
      triggerBtn.setAttribute("aria-busy", "true");
      triggerBtn.disabled = true;
    }
    setStatus(STR.loading, "loading");
    openMapPanel(triggerBtn);

    const geoOptions = {
      enableHighAccuracy: true,
      timeout: 10000,
      maximumAge: 0
    };

    navigator.geolocation.getCurrentPosition(
      function (pos) {
        isGeolocating = false;
        if (triggerBtn) {
          triggerBtn.removeAttribute("aria-busy");
          triggerBtn.disabled = false;
        }

        const coords = pos.coords;
        onUserSelectedLocation(coords.latitude, coords.longitude, "gps", coords.accuracy);

        initOrUpdateMap(coords.latitude, coords.longitude, GPS_ZOOM, true).catch(function () {
          setStatus(STR.error_map_load, "error");
        });

        if (coords.accuracy != null && isFinite(coords.accuracy) && coords.accuracy > 1000) {
          setStatus(STR.warning_gps_approximate, "warn");
          AppDialog.alert({ title: STR.mapTitle, message: STR.warning_gps_approximate, type: "warning" });
        }
      },
      function (err) {
        isGeolocating = false;
        if (triggerBtn) {
          triggerBtn.removeAttribute("aria-busy");
          triggerBtn.disabled = false;
        }

        let errMsg = STR.error_unavailable;
        if (err.code === 1) {
          // PERMISSION_DENIED
          errMsg = STR.error_permission_denied;
        } else if (err.code === 2) {
          // POSITION_UNAVAILABLE
          errMsg = STR.error_unavailable;
        } else if (err.code === 3) {
          // TIMEOUT
          errMsg = STR.error_timeout;
        }

        setStatus(errMsg, "error");
        AppDialog.alert({ title: STR.mapTitle, message: errMsg, type: "error" });

        // Open map centered on existing or default coords so user can pick manually.
        // Pass state coords (possibly null); initOrUpdateMap falls back to DEFAULT_CENTER
        // for the view but only places a marker when real coords exist.
        initOrUpdateMap(
          state.latitude,
          state.longitude,
          state.latitude != null ? GPS_ZOOM : DEFAULT_CENTER.zoom,
          false
        ).catch(function () {
          setStatus(STR.error_map_load, "error");
        });
      },
      geoOptions
    );
  }

  /**
   * Event Listeners setup
   */
  if (chooseMapBtn) {
    chooseMapBtn.addEventListener("click", function () {
      requestGeolocation(chooseMapBtn);
    });
  }

  if (myLocBtn) {
    myLocBtn.addEventListener("click", function () {
      requestGeolocation(myLocBtn);
    });
  }

  if (closeMapBtn) {
    closeMapBtn.addEventListener("click", function () {
      closeMapPanel();
    });
  }

  // Keyboard navigation: Close on Escape key
  document.addEventListener("keydown", function (e) {
    if (e.key === "Escape" && mapPanel && !mapPanel.hidden) {
      closeMapPanel();
    }
  });

  // Address input touch tracking
  if (addressInput) {
    addressInput.addEventListener("input", function () {
      addressTouchedByUser = true;
      hideAutofillStatus();
    });
  }

  // Address suggestion chip actions
  if (addrSuggestBtn) {
    addrSuggestBtn.addEventListener("click", function () {
      const addrInput = document.getElementById("address");
      if (state.formattedAddress && addrInput) {
        addrInput.value = state.formattedAddress;
        addressTouchedByUser = true;
      }
      hideSuggestChip();
      hideAutofillStatus();
    });
  }

  if (addrDismissBtn) {
    addrDismissBtn.addEventListener("click", function () {
      hideSuggestChip();
    });
  }

  /**
   * Public API exposed on window.OrderLocation
   */
  window.OrderLocation = {
    getPayload: function () {
      if (state.latitude == null || state.longitude == null) {
        return {};
      }

      const payload = {
        latitude: Number(state.latitude.toFixed(6)),
        longitude: Number(state.longitude.toFixed(6)),
        locationSource: state.locationSource || "manual",
        locationCapturedAt: state.locationCapturedAt || new Date().toISOString()
      };

      if (state.locationSource === "gps" && state.accuracyMeters != null && isFinite(state.accuracyMeters)) {
        payload.accuracyMeters = Number(state.accuracyMeters);
      }

      if (state.formattedAddress) {
        payload.formattedAddress = state.formattedAddress;
      }

      return payload;
    },

    reset: function () {
      state.latitude = null;
      state.longitude = null;
      state.accuracyMeters = null;
      state.locationSource = null;
      state.locationCapturedAt = null;
      state.formattedAddress = null;

      addressTouchedByUser = false;
      addressAutoFilled = false;
      currentRequestId++;

      if (geocodeDebounceTimer) {
        clearTimeout(geocodeDebounceTimer);
        geocodeDebounceTimer = null;
      }
      if (geocodeAbortController) {
        geocodeAbortController.abort();
        geocodeAbortController = null;
      }

      hideSuggestChip();
      hideAutofillStatus();

      if (markerInstance && mapInstance) {
        mapInstance.removeLayer(markerInstance);
        markerInstance = null;
      }
      if (accuracyCircleInstance && mapInstance) {
        mapInstance.removeLayer(accuracyCircleInstance);
        accuracyCircleInstance = null;
      }

      setStatus("", "");
      closeMapPanel();
    }
  };
})();
