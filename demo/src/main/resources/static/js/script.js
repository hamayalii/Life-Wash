/*
 * Aerie & Wool — per-section scroll choreography + lead form.
 * Pure vanilla JS. No frameworks, no build step.
 *
 * SPATIAL RULE: each rug is scoped to its own section (clipped by that section's
 * overflow:hidden). There is NO single fixed rug crossing the page.
 *   - Section 1 rug (with foam): slides OUT left (translateX(-100vw)) as the
 *     bottom of Section 1 reaches the top of the viewport.
 *   - Section 2 (form): contains no rug at all.
 *   - Section 3 rug (clean): slides IN from the left to centre only once
 *     Section 3 enters the viewport, then holds fixed within the section.
 */
(function () {
  "use strict";

  var s1   = document.getElementById("section1");
  var s3   = document.getElementById("section3");
  var rug1 = document.getElementById("rug1");
  var rug3 = document.getElementById("rug3");

  function clamp(v, lo, hi) { return Math.min(hi, Math.max(lo, v)); }

  // Linear interpolation across keyframe stops [progress, value] for progress p.
  function track(p, stops) {
    if (p <= stops[0][0]) return stops[0][1];
    var last = stops[stops.length - 1];
    if (p >= last[0]) return last[1];
    for (var i = 0; i < stops.length - 1; i++) {
      var a = stops[i], b = stops[i + 1];
      if (p >= a[0] && p <= b[0]) {
        return a[1] + (b[1] - a[1]) * ((p - a[0]) / (b[0] - a[0]));
      }
    }
    return last[1];
  }

  var ticking = false;

  function render() {
    ticking = false;
    var vh = window.innerHeight;

    // ----- Section 1: local progress 0 (top aligned) -> 1 (bottom hits top) -----
    var r1 = s1.getBoundingClientRect();
    var p1 = clamp(-r1.top / r1.height, 0, 1);
    // Pinned for most of the section, then exits fully to the left by p1 = 1.
    var x1 = track(p1, [[0, 0], [0.6, 0], [1, -100]]); // vw
    var a1 = track(p1, [[0, 1], [0.85, 1], [1, 0]]);   // fade as it leaves
    if (rug1) {
      rug1.style.transform = "translateX(" + x1 + "vw)";
      rug1.style.opacity = a1;
    }

    // ----- Section 3: local progress 0 (just entering) -> 1 (filling viewport) --
    var r3 = s3.getBoundingClientRect();
    var p3 = clamp((vh - r3.top) / vh, 0, 1);
    // Slides in from off-screen left to centre, then LOCKS at 0 (stays in section).
    var x3 = track(p3, [[0, -100], [0.55, 0], [1, 0]]); // vw
    if (rug3) {
      rug3.style.transform = "translateX(" + x3 + "vw)";
    }
  }

  function onScroll() {
    if (!ticking) { window.requestAnimationFrame(render); ticking = true; }
  }

  window.addEventListener("scroll", onScroll, { passive: true });
  window.addEventListener("resize", onScroll);
  render();

  // IntersectionObserver: tag whether section 3 is on screen (handy for future
  // water-drop triggers) and reveal sections as they appear.
  if ("IntersectionObserver" in window) {
    var io = new IntersectionObserver(function (entries) {
      entries.forEach(function (e) {
        e.target.classList.toggle("in-view", e.isIntersecting);
      });
    }, { threshold: 0.2 });
    document.querySelectorAll(".section").forEach(function (s) { io.observe(s); });
  }

  // ── Quantity field: show/hide and configure per rugType ───────────────────────
  //
  // Quantity config keyed by rugType value:
  //   label  — Kurdish label text shown above the input
  //   step   — input step attribute ("0.1" for decimal metres, "1" for integers)
  //   min    — input min attribute
  //   required — whether the backend requires quantity (antique does NOT)
  var QUANTITY_CONFIG = {
    persian:   { label: "بڕی مەتر بنووسە (بۆ نموونە: 3.5)", step: "0.1", min: "0.1" },
    rug:       { label: "بڕی مەتر بنووسە (بۆ نموونە: 3.5)", step: "0.1", min: "0.1" },
    carpet:    { label: "بڕی مەتر بنووسە (بۆ نموونە: 3.5)", step: "0.1", min: "0.1" },
    shag:      { label: "بڕی مەتر بنووسە (بۆ نموونە: 3.5)", step: "0.1", min: "0.1" },
    silk:      { label: "چەند دانە بەتانییە؟ بە ژمارە بنووسە", step: "1", min: "1" },
    synthetic: { label: "چەند دانە تەنکییە؟ بە ژمارە بنووسە", step: "1", min: "1" },
    wool:      { label: "قەنەفەکت چەند نەفەرییە؟ بە ژمارە بنووسە", step: "1", min: "1" }
    // antique → field stays hidden (not in map)
  };

  var quantityField  = document.getElementById("quantityField");
  var quantityInput  = document.getElementById("quantity");
  var quantityLabel  = document.getElementById("quantityLabel");
  var selectedServiceIdInput = document.getElementById("selectedServiceId");
  var selectedServiceNameInput = document.getElementById("selectedServiceName");

  // ---- Lead form submission via native fetch() to the Spring Boot backend ----
  var form      = document.getElementById("leadForm");
  var status    = document.getElementById("formStatus");
  var API_ENDPOINT = "/api/v1/orders";

  form.addEventListener("submit", function (e) {
    e.preventDefault();
    status.className   = "form-status";
    status.textContent = "";

    // Check if cart has items
    if (!window.GhaslServicesState || !window.GhaslServicesState.cart || window.GhaslServicesState.cart.items.length === 0) {
      status.className   = "form-status err";
      status.textContent = "تکایە خزمەتگوزارییەک هەڵبژێرە.";
      return;
    }

    // Check if all quantities are filled
    const allQuantitiesFilled = window.GhaslServicesState.cart.items.every(item => 
      item.quantity !== null && item.quantity > 0
    );
    if (!allQuantitiesFilled) {
      status.className   = "form-status err";
      status.textContent = "تکایە بڕی هەموو خزمەتگوزارییەکان بنووسە.";
      return;
    }
    
    // ── Client-side validation ────────────────────────────────────────────────
    if (!form.name.value.trim() || !form.phone.value.trim()) {
      status.className   = "form-status err";
      status.textContent = "تکایە ناو و ژمارە تەلەفۆنەکەت بنووسە بۆ ئەوەی پەیوەندیت پێوە دەکەین.";
      return;
    }

    // CRITICAL FIX: Include idempotencyKey in request body for duplicate prevention
    var payload = {
      customerName: form.name.value.trim(),
      phoneNumber:  form.phone.value.trim(),
      address:      form.address.value.trim(),
      notes:        form.message.value.trim(),
      createdBy:    'WEBSITE',
      idempotencyKey: getIdempotencyKey('web_idempotency_key'),  // Use global utility with storage key
      items: window.GhaslServicesState.cart.items.map(item => {
        if (!item.serviceId) {
          throw new Error('Service ID missing - cannot submit order');
        }
        return {
          serviceId: item.serviceId,
          quantity: item.quantity,
          unitName: item.unitName ? item.unitName.toUpperCase() : 'PER_PIECE',
          unitPrice: item.basePrice,
          totalPrice: item.totalPrice
        };
      })
    };

    var btn = form.querySelector('button[type="submit"]');
    btn.disabled    = true;
    btn.textContent = "ناردن...";

    // CRITICAL FIX: Idempotency key is now in request body, not header
    fetch(API_ENDPOINT, {
      method:  "POST",
      credentials: 'include', // Include HttpOnly JWT cookie for authentication
      headers: { 
        "Content-Type": "application/json"
      },
      body:    JSON.stringify(payload)
    })
      .then(function (res) {
        if (res.status === 201) {
          return res.json().catch(function () { return {}; });
        } else if (res.status === 400) {
          return res.json().then(function (body) {
            throw new Error(body.message || "زانیارییەکانت دووبارە بپشکنە.");
          });
        } else {
          throw new Error("Request failed with status " + res.status);
        }
      })
      .then(function (body) {
        var banner = document.getElementById("successBanner");
        var bannerText = document.getElementById("successBannerText");
        bannerText.textContent = "داواکەت بە سەرکەوتوویی گەیشت! چاوەڕێی پەیوەندیمان بکە";
        banner.classList.add("active");
        setTimeout(function() {
          banner.classList.remove("active");
        }, 5000);

        // CRITICAL FIX: Clear idempotency key after successful order
        clearIdempotencyKey('web_idempotency_key');

        // Reset form and cart
        form.reset();
        window.GhaslServicesState.cart.items = [];
        window.GhaslServicesState.cart.total = 0;
        
        // Re-render cart and inputs
        if (window.renderCart) window.renderCart();
        if (window.renderDynamicInputs) window.renderDynamicInputs();
        if (window.updateTotalDisplay) window.updateTotalDisplay();
        if (window.validateCartForSubmission) window.validateCartForSubmission();
      })
      .catch(function (err) {
        status.className   = "form-status err";
        status.textContent = err.message || "هەڵەیەک ڕووی دا. تکایە پەیوەندی بکە بە: ٠٧٧٠١٤٦٤٢٤٨";
        console.error(err);
      })
      .finally(function () {
        btn.disabled    = false;
        btn.textContent = "داواکردنی تیمەکانمان";
      });
  });

  // ---- Admin Authentication Logic ----
  var loginBtn = document.getElementById("loginBtn");
  var loginModal = document.getElementById("loginModal");
  var closeLoginModal = document.getElementById("closeLoginModal");
  var modalBackdrop = document.getElementById("modalBackdrop");
  var adminMenu = document.getElementById("adminMenu");
  var adminToggle = document.getElementById("adminToggle");
  var adminDropdown = document.querySelector(".admin-dropdown");
  var logoutBtn = document.getElementById("logoutBtn");
  var loginForm = document.getElementById("loginForm");
  var loginStatus = document.getElementById("loginStatus");

  function setAuthenticatedState(isAuthenticated) {
    if (isAuthenticated) {
      if(loginBtn) loginBtn.style.display = "none";
      if(adminMenu) adminMenu.classList.remove("hidden");
    } else {
      if(loginBtn) loginBtn.style.display = "";
      if(adminMenu) adminMenu.classList.add("hidden");
    }
  }

  // Check auth state on load
  fetch("/api/v1/auth/check", { 
    method: "GET",
    credentials: 'include' // Include HttpOnly JWT cookie for authentication
  })
    .then(function(res) {
      if(res.ok) {
         setAuthenticatedState(true);
      }
    })
    .catch(console.error);

  // Client-side route guard logic if they try to access protected paths manually
  if (window.location.pathname === "/pos" || window.location.pathname === "/reports" || window.location.pathname === "/dashboard.html") {
    fetch("/api/v1/auth/check", { 
      method: "GET",
      credentials: 'include' // Include HttpOnly JWT cookie for authentication
    })
      .then(function(res) {
         if(res.status !== 200) window.location.href = "/";
      });
  }

  if (loginBtn) {
    loginBtn.addEventListener("click", function () {
      loginModal.classList.remove("hidden");
    });
  }

  function closeLogin() {
    loginModal.classList.add("hidden");
    loginForm.reset();
    loginStatus.textContent = "";
    loginStatus.className = "form-status";
  }

  if (closeLoginModal) closeLoginModal.addEventListener("click", closeLogin);
  if (modalBackdrop) modalBackdrop.addEventListener("click", closeLogin);

  var dropdownOpenTime = 0;

  if (adminToggle) {
    adminToggle.addEventListener("mousedown", function (e) {
      e.stopPropagation();
      var isHidden = adminDropdown.classList.contains("hidden");
      adminDropdown.classList.toggle("hidden");
      dropdownOpenTime = Date.now();

      if (!isHidden) {
        // Position dropdown at toggle button's location
        var rect = adminToggle.getBoundingClientRect();
        adminDropdown.style.top = (rect.bottom + 4) + "px";
        adminDropdown.style.right = (window.innerWidth - rect.right) + "px";
      }
    });
  }

  // Close dropdown if clicked outside
  document.addEventListener("click", function (e) {
    // Don't close if clicked within 100ms of opening (prevents immediate closing)
    if (Date.now() - dropdownOpenTime < 100) return;
    if (adminMenu && !e.target.closest("#adminMenu") && !adminDropdown.classList.contains("hidden")) {
      adminDropdown.classList.add("hidden");
    }
  });

  // Prevent clicks inside dropdown from bubbling to document
  if (adminDropdown) {
    adminDropdown.addEventListener("click", function (e) {
      e.stopPropagation();
    });
  }

  if (loginForm) {
    loginForm.addEventListener("submit", function (e) {
      e.preventDefault();
      loginStatus.className = "form-status";
      loginStatus.textContent = "";
      var btn = loginForm.querySelector("button");
      btn.disabled = true;

      fetch("/api/v1/auth/login", {
        method: "POST",
        credentials: 'include', // Include HttpOnly JWT cookie for authentication
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          username: loginForm.username.value,
          password: loginForm.password.value
        })
      })
      .then(function(res) {
        if (res.ok) return res.json();
        throw new Error("ناوی بەکارهێنەر یان وشەی نهێنی هەڵەیە");
      })
      .then(function() {
        setAuthenticatedState(true);
        closeLogin();
      })
      .catch(function(err) {
        loginStatus.className = "form-status err";
        loginStatus.textContent = err.message;
      })
      .finally(function() {
        btn.disabled = false;
      });
    });
  }

  if (logoutBtn) {
    logoutBtn.addEventListener("click", function () {
      fetch("/api/v1/auth/logout", { 
        method: "POST",
        credentials: 'include' // Include HttpOnly JWT cookie for authentication
      })
        .then(function() {
          setAuthenticatedState(false);
          adminDropdown.classList.add("hidden");
        });
    });
  }
})();

