/**
 * Dynamic Service Cards with Cart System for Customer Order Form
 * Single Source of Truth: GET /api/v1/services/active
 * 
 * Security: Safe DOM manipulation (createElement, no innerHTML with unsanitized data)
 * Performance: Event delegation (single listener on parent container)
 * State Management: Fetch once, store in window.GhaslServicesState
 * Cart System: Multi-item selection with Kurdish ordinal numbering
 */

(function() {
  "use strict";

  // Global state for services (Single Source of Truth)
  window.GhaslServicesState = {
    services: [],
    cart: {
      items: [],
      total: 0
    }
  };

  // DOM Elements
  const serviceCardsContainer = document.getElementById('service-cards-container');
  const cartContainer = document.getElementById('cart-container');
  const cartItemsContainer = document.getElementById('cart-items');
  const cartTotalElement = document.getElementById('cart-total');
  const dynamicInputsContainer = document.getElementById('dynamic-inputs-container');
  const cartItemInputsContainer = document.getElementById('cart-item-inputs');
  const submitBtn = document.getElementById('submitBtn');

  /**
   * Helper function to add Kurdish definite suffix
   * Rule: If word ends with 'ە', remove 'ە' and append 'ەکە' (e.g., قەنەفە → قەنەفەکە)
   * If word ends with 'ی', 'ۆ', 'ێ', 'ا', append 'کە'
   * Otherwise (consonants like فەرش, سەربان), append 'ەکە'
   */
  function addKurdishDefiniteSuffix(word) {
    if (!word) return '';
    const lastChar = word.slice(-1);
    
    if (lastChar === 'ە') {
      // Remove 'ە' and append 'ەکە' (e.g., قەنەفە → قەنەفەکە)
      return word.slice(0, -1) + 'ەکە';
    }
    if (['ی', 'ۆ', 'ێ', 'ا'].includes(lastChar)) {
      // Append 'کە' for words ending with these vowels
      return word + 'کە';
    }
    // Otherwise append 'ەکە' (consonants like فەرش, سەربان)
    return word + 'ەکە';
  }

  /**
   * Dynamic Price Calculation: P(q) = (B / S) * q
   * Mathematical determinism for Zero Trust Architecture
   * 
   * @param {Object} serviceItem - Service object from API (window.GhaslServicesState)
   * @param {number} userQuantity - Quantity requested by user
   * @returns {number} Calculated price
   */
  function calculateDynamicPrice(serviceItem, userQuantity) {
    const basePrice = parseFloat(serviceItem.basePrice);
    if (isNaN(basePrice)) {
      console.error("Critical: Invalid base price from API.");
      return 0;
    }

    if (serviceItem.unitName === 'PER_PERSON') {
      // Fallback to 10 ONLY if backend omits it, to prevent division by zero or NaN
      const setSize = (serviceItem.sofaStandardSetSize && serviceItem.sofaStandardSetSize > 0) 
                      ? parseInt(serviceItem.sofaStandardSetSize) 
                      : 10; 
      const pricePerUnit = basePrice / setSize;
      return pricePerUnit * userQuantity;
    }
    
    // Default calculation for PER_PIECE, PER_METER, etc.
    return basePrice * userQuantity;
  }

  // Kurdish ordinal array
  const KURDISH_ORDINALS = [
    'یەکەم', 'دووەم', 'سێیەم', 'چوارەم', 'پێنجەم',
    'شەشەم', 'حەوتەم', 'هەشتەم', 'نۆیەم', 'دەیەم'
  ];

  // Unit label mappings for Kurdish display
  const UNIT_LABELS = {
    'PER_METER': 'مەتر',
    'PER_PIECE': 'دانە',
    'PER_PERSON': 'نەفەر',
    'COUNT': 'دانە',
    'HOURLY': 'کاتژمێر',
    'PER_SQUARE_METER': 'مەتری دووجا',
    'PER_KILOGRAM': 'کیلۆگرام',
    'PER_LITER': 'لیتر',
    'JOB': 'کار'
  };

  // Unit type configurations for input validation
  const UNIT_CONFIG = {
    integer: ['PER_PIECE', 'PER_PERSON', 'COUNT', 'JOB'],
    decimal: ['PER_METER', 'PER_SQUARE_METER', 'PER_KILOGRAM', 'PER_LITER', 'HOURLY']
  };

  /**
   * Generate unique ID for cart items
   */
  function generateUniqueId() {
    return Date.now().toString(36) + Math.random().toString(36).substr(2);
  }

  /**
   * Kurdish ordinal label generator
   */
  function generateCartItemLabel(serviceName, unitQuestion, occurrenceIndex, totalOccurrences) {
    if (totalOccurrences === 1) {
      return `${serviceName} ${unitQuestion}`;
    }
    const ordinal = KURDISH_ORDINALS[occurrenceIndex] || (occurrenceIndex + 1);
    return `${serviceName}ی ${ordinal} ${unitQuestion}`;
  }

  /**
   * Calculate cart total with discount logic using P(q) = (B / S) * q
   */
  function calculateCartTotal() {
    let total = 0;
    window.GhaslServicesState.cart.items.forEach(item => {
      if (item.quantity !== null && item.quantity > 0) {
        // Use dynamic calculation for PER_PERSON services
        const effectivePrice = item.basePrice - (item.discount || 0);
        const serviceItem = {
          basePrice: effectivePrice,
          unitName: item.unitName,
          sofaStandardSetSize: item.sofaStandardSetSize
        };
        const itemTotal = calculateDynamicPrice(serviceItem, item.quantity);
        item.totalPrice = itemTotal;
        total += itemTotal;
      } else {
        item.totalPrice = 0;
      }
    });
    window.GhaslServicesState.cart.total = total;
    updateTotalDisplay();
    validateCartForSubmission();
  }

  /**
   * Update total display
   */
  function updateTotalDisplay() {
    if (cartTotalElement) {
      cartTotalElement.textContent = `${window.GhaslServicesState.cart.total.toLocaleString()} IQD`;
    }
  }

  /**
   * Add item to cart
   */
  function addToCart(service) {
    const cartItem = {
      id: generateUniqueId(),
      serviceId: service.coreServiceId || service.id,
      serviceName: service.kurdishName,
      basePrice: service.activePrice,
      discount: service.discountActive ? (service.activePrice - service.discountedPrice) : 0,
      unitName: service.measurementUnit,
      sofaStandardSetSize: service.sofaStandardSetSize,
      quantity: null,
      totalPrice: 0
    };
    window.GhaslServicesState.cart.items.push(cartItem);
    renderCart();
    renderDynamicInputs();
    calculateCartTotal();
  }

  /**
   * Remove item from cart
   */
  function removeFromCart(itemId) {
    window.GhaslServicesState.cart.items = window.GhaslServicesState.cart.items.filter(item => item.id !== itemId);
    renderCart();
    renderDynamicInputs();
    calculateCartTotal();
  }

  /**
   * Update item quantity
   */
  function updateItemQuantity(itemId, quantity) {
    const item = window.GhaslServicesState.cart.items.find(i => i.id === itemId);
    if (item) {
      item.quantity = quantity ? parseFloat(quantity) : null;
      calculateCartTotal();
    }
  }

  /**
   * Render cart display with safe DOM manipulation
   */
  function renderCart() {
    if (!cartItemsContainer) return;
    cartItemsContainer.innerHTML = '';
    
    if (window.GhaslServicesState.cart.items.length === 0) {
      if (cartContainer) cartContainer.style.display = 'none';
      return;
    }
    
    if (cartContainer) cartContainer.style.display = 'block';
    
    window.GhaslServicesState.cart.items.forEach(item => {
      const cartItem = document.createElement('div');
      cartItem.className = 'cart-item';
      cartItem.dataset.itemId = item.id;
      
      // Cart item info container
      const cartItemInfo = document.createElement('div');
      cartItemInfo.className = 'cart-item-info';
      
      // Service name
      const serviceName = document.createElement('h5');
      serviceName.textContent = item.serviceName;
      cartItemInfo.appendChild(serviceName);
      
      // Dynamic price from state
      const priceElement = document.createElement('p');
      priceElement.className = 'cart-item-price';
      const displayPrice = item.totalPrice || item.basePrice;
      priceElement.textContent = `${displayPrice.toLocaleString()} IQD`;
      if (item.discount > 0) {
        priceElement.textContent += ` (داشکاندن: ${item.discount.toLocaleString()})`;
      }
      cartItemInfo.appendChild(priceElement);
      
      cartItem.appendChild(cartItemInfo);
      
      // Remove button with trash icon
      const removeBtn = document.createElement('button');
      removeBtn.type = 'button';
      removeBtn.className = 'btn-remove-item';
      removeBtn.setAttribute('aria-label', 'Remove item');
      
      const trashIcon = document.createElement('i');
      trashIcon.className = 'fa-solid fa-trash';
      removeBtn.appendChild(trashIcon);
      
      cartItem.appendChild(removeBtn);
      cartItemsContainer.appendChild(cartItem);
    });
  }

  // Export cart functions globally for script.js access
  window.renderCart = renderCart;
  window.renderDynamicInputs = renderDynamicInputs;
  window.updateTotalDisplay = updateTotalDisplay;
  window.validateCartForSubmission = validateCartForSubmission;

  /**
   * Render dynamic quantity inputs with safe DOM manipulation and MeasurementUnit mapping
   */
  function renderDynamicInputs() {
    if (!cartItemInputsContainer) return;
    cartItemInputsContainer.innerHTML = '';
    
    if (window.GhaslServicesState.cart.items.length === 0) {
      if (dynamicInputsContainer) dynamicInputsContainer.style.display = 'none';
      return;
    }
    
    if (dynamicInputsContainer) dynamicInputsContainer.style.display = 'block';
    
    // Group items by service name to calculate occurrences
    const serviceGroups = {};
    window.GhaslServicesState.cart.items.forEach((item, index) => {
      if (!serviceGroups[item.serviceName]) {
        serviceGroups[item.serviceName] = [];
      }
      serviceGroups[item.serviceName].push({ item, index });
    });
    
    window.GhaslServicesState.cart.items.forEach((item, index) => {
      const group = serviceGroups[item.serviceName];
      const occurrenceIndex = group.findIndex(g => g.index === index);
      const totalOccurrences = group.length;
      
      // Get the service from global state to access measurementUnit
      const service = window.GhaslServicesState.services.find(s => 
        s.coreServiceId === item.serviceId || s.id === item.serviceId
      );
      
      // Map MeasurementUnit to localized question WITHOUT service name prefix
      // The service name will be added by generateCartItemLabel to avoid duplication
      let unitQuestion;
      const measurementUnit = service ? service.measurementUnit : item.unitName;
      
      if (measurementUnit === 'PER_METER') {
        unitQuestion = 'چەند مەترە؟';
      } else if (measurementUnit === 'PER_PIECE' || measurementUnit === 'COUNT') {
        unitQuestion = 'چەند دانەیە؟';
      } else if (measurementUnit === 'PER_PERSON') {
        unitQuestion = 'چەند نەفەرییە؟';
      } else {
        // Fallback for other units
        unitQuestion = UNIT_QUESTIONS[measurementUnit] || 'چەند؟';
      }
      
      const label = generateCartItemLabel(item.serviceName, unitQuestion, occurrenceIndex, totalOccurrences);
      
      const inputField = document.createElement('div');
      inputField.className = 'field';
      
      const labelElement = document.createElement('label');
      labelElement.setAttribute('for', `quantity-${item.id}`);
      labelElement.textContent = label;
      inputField.appendChild(labelElement);
      
      const input = document.createElement('input');
      input.type = 'number';
      input.id = `quantity-${item.id}`;
      input.name = `quantity-${item.id}`;
      input.min = '0.1';
      input.step = '0.1';
      input.required = true;
      input.placeholder = 'بڕ بنووسە';
      input.value = item.quantity || '';  // Preserve state from cart
      inputField.appendChild(input);
      
      cartItemInputsContainer.appendChild(inputField);
      
      // Add input change listener
      input.addEventListener('input', (e) => {
        updateItemQuantity(item.id, e.target.value);
      });
    });
  }

  /**
   * Validate cart for submission
   */
  function validateCartForSubmission() {
    if (!submitBtn) return;
    const hasItems = window.GhaslServicesState.cart.items.length > 0;
    const allQuantitiesFilled = window.GhaslServicesState.cart.items.every(item => 
      item.quantity !== null && item.quantity > 0
    );
    submitBtn.disabled = !(hasItems && allQuantitiesFilled);
  }

  /**
   * Fetch active services from API (called once on DOM load)
   */
  async function fetchActiveServices() {
    try {
      const response = await fetch('/api/v1/services/active');
      if (!response.ok) {
        throw new Error('Failed to fetch services: ' + response.status);
      }

      const services = await response.json();
      
      // Store in global state for reuse without hitting backend again
      window.GhaslServicesState.services = services;
      
      // Render service cards
      renderServiceCards(services);
      
    } catch (error) {
      console.error('Error fetching active services:', error);
      renderErrorState(error.message);
    }
  }

  /**
   * Render service cards using safe DOM manipulation
   * No innerHTML with unsanitized data - uses createElement
   */
  function renderServiceCards(services) {
    // Clear loading state
    serviceCardsContainer.innerHTML = '';
    
    if (!services || services.length === 0) {
      renderEmptyState();
      return;
    }

    // Create cards using safe DOM manipulation
    services.forEach(service => {
      const card = createServiceCard(service);
      serviceCardsContainer.appendChild(card);
    });
  }

  /**
   * Create a single service card element safely
   */
  function createServiceCard(service) {
    const card = document.createElement('div');
    card.className = 'service-card';
    card.dataset.serviceId = service.id;
    card.dataset.serviceName = service.kurdishName;
    card.dataset.englishName = service.englishName;
    card.dataset.measurementUnit = service.measurementUnit;
    card.dataset.activePrice = service.activePrice;
    card.dataset.discountedPrice = service.discountedPrice || '';
    card.dataset.discountActive = service.discountActive;
    card.dataset.customPriced = service.customPriced;
    card.dataset.coreServiceId = service.coreServiceId || service.id;

    // Icon
    const icon = document.createElement('div');
    icon.className = 'service-card-icon';
    const iconElement = document.createElement('i');
    // Use the iconUrl from backend (FontAwesome class)
    iconElement.className = `fa-solid ${service.iconUrl || 'fa-circle'}`;
    icon.appendChild(iconElement);

    // Service name (safe textContent, not innerHTML)
    const name = document.createElement('div');
    name.className = 'service-card-name';
    name.textContent = service.kurdishName;

    // Price display
    const price = document.createElement('div');
    price.className = 'service-card-price';
    
    if (service.customPriced) {
      price.textContent = 'نرخی دیاری نەکراوە';
    } else if (service.discountActive && service.discountedPrice && service.discountedPrice < service.activePrice) {
      // Discounted price display
      const oldPriceSpan = document.createElement('span');
      oldPriceSpan.className = 'old-price';
      oldPriceSpan.textContent = formatPrice(service.activePrice);
      
      const discountSpan = document.createElement('span');
      discountSpan.className = 'discount-badge';
      discountSpan.textContent = formatPrice(service.discountedPrice);
      
      const unitLabel = UNIT_LABELS[service.measurementUnit] || service.measurementUnit;
      const unitText = document.createTextNode(` IQD/${unitLabel}`);
      
      price.appendChild(oldPriceSpan);
      price.appendChild(discountSpan);
      price.appendChild(unitText);
    } else {
      // Regular price display
      const unitLabel = UNIT_LABELS[service.measurementUnit] || service.measurementUnit;
      price.textContent = `${formatPrice(service.activePrice)} IQD/${unitLabel}`;
    }

    // Assemble card
    card.appendChild(icon);
    card.appendChild(name);
    card.appendChild(price);

    return card;
  }

  /**
   * Format price with locale-specific formatting
   */
  function formatPrice(price) {
    if (!price) return '0';
    return parseFloat(price).toLocaleString('ku-IQ');
  }

  /**
   * Render error state
   */
  function renderErrorState(errorMessage) {
    serviceCardsContainer.innerHTML = '';
    const errorDiv = document.createElement('div');
    errorDiv.className = 'error-state';
    
    const errorIcon = document.createElement('i');
    errorIcon.className = 'fa-solid fa-triangle-exclamation';
    
    const errorText = document.createElement('p');
    errorText.textContent = 'هەڵە لە بارکردنی خزمەتگوزارییەکان';
    
    const errorDetail = document.createElement('p');
    errorDetail.className = 'error-detail';
    errorDetail.textContent = errorMessage;
    
    errorDiv.appendChild(errorIcon);
    errorDiv.appendChild(errorText);
    errorDiv.appendChild(errorDetail);
    serviceCardsContainer.appendChild(errorDiv);
  }

  /**
   * Render empty state
   */
  function renderEmptyState() {
    serviceCardsContainer.innerHTML = '';
    const emptyDiv = document.createElement('div');
    emptyDiv.className = 'error-state';
    
    const emptyIcon = document.createElement('i');
    emptyIcon.className = 'fa-solid fa-inbox';
    
    const emptyText = document.createElement('p');
    emptyText.textContent = 'هیچ خزمەتگوزارییەک بەردەست نییە';
    
    emptyDiv.appendChild(emptyIcon);
    emptyDiv.appendChild(emptyText);
    serviceCardsContainer.appendChild(emptyDiv);
  }

  /**
   * Handle service card selection via Event Delegation
   * Changed from single selection to cart addition (multi-item support)
   */
  function handleServiceCardClick(event) {
    // Find the closest service card ancestor
    const card = event.target.closest('.service-card');
    if (!card) return;

    // Get service data from card dataset
    const serviceId = card.dataset.serviceId;
    const serviceName = card.dataset.serviceName;
    const measurementUnit = card.dataset.measurementUnit;
    
    // Find the service in global state to get full data
    const service = window.GhaslServicesState.services.find(s => s.id == serviceId);
    if (service) {
      addToCart(service);
    }
  }

  // Event Delegation: Single listener on parent container for service cards
  serviceCardsContainer.addEventListener('click', handleServiceCardClick);
  
  // Event Delegation: Single listener on cart container for remove buttons
  if (cartItemsContainer) {
    cartItemsContainer.addEventListener('click', (e) => {
      const removeBtn = e.target.closest('.btn-remove-item');
      if (removeBtn) {
        const cartItem = removeBtn.closest('.cart-item');
        if (cartItem && cartItem.dataset.itemId) {
          removeFromCart(cartItem.dataset.itemId);
        }
      }
    });
  }

  // Initialize: Fetch services on DOM load
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', fetchActiveServices);
  } else {
    fetchActiveServices();
  }

})();
