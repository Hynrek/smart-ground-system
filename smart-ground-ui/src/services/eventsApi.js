/* global EventSource */
import { BASE_URL } from './apiClient.js';

export function subscribeToEvents(onMessage, onError, onClose) {
  // Note: EventSource does not support custom headers in all browsers.
  // The backend must allow the SSE endpoint without auth header, or use a token query param.
  // For now we open without auth headers (the backend session cookie / token is sent automatically
  // if the EventSource URL includes credentials).
  const url = `${BASE_URL}/events`;
  const eventSource = new EventSource(url);

  const handleEvent = (event) => {
    try {
      const data = JSON.parse(event.data);
      onMessage({ type: event.type, ...data });
    } catch (error) {
      console.error('Failed to parse SSE event data:', error);
    }
  };

  eventSource.addEventListener('smartbox.status', handleEvent);
  eventSource.addEventListener('smartbox.synced', handleEvent);
  eventSource.addEventListener('device.health', handleEvent);

  // Fallback for generic messages
  eventSource.onmessage = (event) => {
    try {
      const data = JSON.parse(event.data);
      onMessage(data);
    } catch (error) {
      console.error('Failed to parse SSE message:', error);
    }
  };

  eventSource.onerror = (error) => {
    console.error('SSE connection error:', error);
    if (onError) onError(error);
  };

  return {
    close: () => {
      eventSource.close();
      if (onClose) onClose();
    },
  };
}
