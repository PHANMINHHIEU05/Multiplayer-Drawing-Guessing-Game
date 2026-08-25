
export interface WSRequest<T = any> {
  type: string;
  requestId: string;
  payload: T;
}

export interface WSResponse<T = any> {
  type: string;
  requestId?: string;
  roomId?: string;
  playerId?: string;
  username?: string;
  code?: string;
  message?: string;
  error?: {
    code: string;
    message: string;
  };
  payload?: T;
  [key: string]: any;
}

export type MessageHandler = (response: WSResponse) => void;
