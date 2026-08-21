import React, { createContext, useContext, useEffect } from 'react';
import { WebSocketClient, wsClient } from './WebSocketClient';
import { setupMessageHandlers } from './messageHandlers';

const WebSocketContext = createContext<WebSocketClient>(wsClient);

export const WebSocketProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  useEffect(() => {
    wsClient.connect();

    const handler = setupMessageHandlers();
    const removeListener = wsClient.addMessageListener(handler);

    return () => {
      removeListener();
      wsClient.disconnect();
    };
  }, []);

  return <WebSocketContext.Provider value={wsClient}>{children}</WebSocketContext.Provider>;
};

export const useWebSocket = (): WebSocketClient => {
  return useContext(WebSocketContext);
};
