import { useSyncExternalStore } from "react";
import { ChatMessage } from "../types/chat";

interface LobbyChatStoreState {
  messages: ChatMessage[];
}

let state: LobbyChatStoreState = {
  messages: [],
};

const listeners = new Set<() => void>();

function notify() {
  listeners.forEach((l) => l());
}

export const lobbyChatStore = {
  getState: () => state,
  addMessage: (message: ChatMessage) => {
    // Avoid duplicate messageId if exists
    if (
      message.messageId &&
      state.messages.some((m) => m.messageId === message.messageId)
    ) {
      return;
    }
    state = {
      messages: [...state.messages, message].slice(-50), // keep only recent 50
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

export function useLobbyChatStore<T>(
  selector: (state: LobbyChatStoreState) => T,
): T {
  return useSyncExternalStore(
    lobbyChatStore.subscribe,
    () => selector(lobbyChatStore.getState()),
    () => selector(lobbyChatStore.getState()),
  );
}
