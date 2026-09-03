/**
 * Unified Rejection Modal Component
 * Reusable across Dashboard, POS, and other interfaces
 * Follows Single Source of Truth principle - fetches reasons from API
 */

// Global state for rejection reasons
window.RejectionModalState = {
  reasons: [],
  loading: false,
  currentOrderId: null,
  callback: null
};

/**
 * Show rejection modal for a specific order
 * @param {number} orderId - The order ID to reject
 * @param {function} callback - Function to call after successful rejection
 */
async function showRejectionModal(orderId, callback) {
  window.RejectionModalState.currentOrderId = orderId;
  window.RejectionModalState.callback = callback;
  
  // Fetch rejection reasons if not already loaded
  if (window.RejectionModalState.reasons.length === 0) {
    await fetchRejectionReasons();
  }
  
  // Render modal with reasons
  renderRejectionModal();
  
  // Show modal
  const modal = document.getElementById('rejection-modal');
  if (modal) {
    modal.classList.add('active');
  }
}

/**
 * Fetch rejection reasons from API (Single Source of Truth)
 */
async function fetchRejectionReasons() {
  try {
    const response = await fetch('/api/v1/orders/rejection-reasons');
    
    if (!response.ok) {
      throw new Error(`Failed to fetch rejection reasons: ${response.status}`);
    }
    
    const data = await response.json();
    window.RejectionModalState.reasons = data;
    
  } catch (error) {
    console.error('Error fetching rejection reasons:', error);
    alert('هەڵە لە بارکردنی هۆکارەکانی ڕەتکردنەوە');
  }
}

/**
 * Render rejection modal with dynamic buttons from API
 */
function renderRejectionModal() {
  const modal = document.getElementById('rejection-modal');
  if (!modal) return;
  
  // Clear existing content
  const buttonsContainer = modal.querySelector('.rejection-buttons-container');
  if (buttonsContainer) {
    buttonsContainer.innerHTML = '';
  }
  
  // Create buttons dynamically from API data
  window.RejectionModalState.reasons.forEach(reason => {
    const button = document.createElement('button');
    button.className = 'rejection-reason-btn';
    button.dataset.reason = reason.value;
    
    // Safe DOM manipulation - use textContent
    const buttonText = document.createTextNode(reason.kurdishLabel);
    button.appendChild(buttonText);
    
    button.addEventListener('click', () => handleRejection(reason.value));
    
    buttonsContainer.appendChild(button);
  });
}

/**
 * Handle rejection when a reason is selected
 */
async function handleRejection(reasonValue) {
  const orderId = window.RejectionModalState.currentOrderId;
  
  try {
    const response = await fetch(`/api/v1/orders/${orderId}/reject`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        rejectionReason: reasonValue
      })
    });
    
    if (response.status === 409) {
      // Conflict - show conflict modal if available
      if (typeof showConflictModal === 'function') {
        closeRejectionModal();
        showConflictModal(orderId, 'reject');
      } else {
        alert('هەڵە: ئەم داواکارییە لەلایەن بەکارهێنەرێکی ترەوە دەستکاری کراوە. تکایە دووبارە هەوڵبدەرەوە.');
      }
      return;
    }
    
    if (!response.ok) {
      const errorText = await response.text();
      throw new Error(errorText || `Failed to reject order: ${response.status}`);
    }
    
    // Close modal
    closeRejectionModal();
    
    // Execute callback if provided
    if (window.RejectionModalState.callback) {
      window.RejectionModalState.callback();
    }
    
  } catch (error) {
    console.error('Error rejecting order:', error);
    alert('هەڵە لە ڕەتکردنەوەی داواکارییەکە: ' + error.message);
  }
}

/**
 * Close rejection modal
 */
function closeRejectionModal() {
  const modal = document.getElementById('rejection-modal');
  if (modal) {
    modal.classList.remove('active');
  }
  
  // Reset state
  window.RejectionModalState.currentOrderId = null;
  window.RejectionModalState.callback = null;
}

// Export functions for use in other files
window.showRejectionModal = showRejectionModal;
window.closeRejectionModal = closeRejectionModal;
