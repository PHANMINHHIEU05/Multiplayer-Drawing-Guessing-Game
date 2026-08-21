import { useSyncExternalStore } from 'react';
import { ChatMessage } from '../types/chat';

interface ChatStoreState {
  messages: ChatMessage[];
}

let state: ChatStoreState = {
  messages: [],
};

const listeners = new Set<() => void>();

function notify() {
  listeners.forEach((l) => l());
}

export const chatStore = {
  getState: () => state,
  addMessage: (message: ChatMessage) => {
    // Avoid duplicate messageId if exists
    if (message.messageId && state.messages.some((m) => m.messageId === message.messageId)) {
      return;
    }
    state = {
      messages: [...state.messages, message],
    };
    notify();
  },
  setMessages: (messages: ChatMessage[]) => {
    state = { messages };
    notify();
  },
  clearMessages: () => {
    state = { messages: [] };
    notify();
  },
  subscribe: (listener: () => void) => {
    listeners.add(listener);
    return () => listeners.delete(listener);
  },
};

export function useChatStore<T>(selector: (state: ChatStoreState) => T): T {
  return useSyncExternalStore(
    chatStore.subscribe,
    () => selector(chatStore.getState()),
    () => selector(chatStore.getState())
  );
}
