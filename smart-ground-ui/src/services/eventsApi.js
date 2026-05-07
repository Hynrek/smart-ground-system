/* global EventSource */
import { BASE_URL } from './apiClient.js';

export function subscribeToEvents(onMessage, onError, onClose) {
  const url = `${BASE_URL}/events`;

  const eventSource = new EventSource(url);

  eventSource.onmessage = (event) => {
    try {
      const data = JSON.parse(event.data);
      onMessage(data);
    } catch (error) {
      console.error('Failed to parse event data:', error);
    }
  };

  eventSource.onerror = (error) => {
    console.error('EventSource error:', error);
    if (onError) onError(error);
  };

  return {
    close: () => {
      eventSource.close();
      if (onClose) onClose();
    },
  };
}
