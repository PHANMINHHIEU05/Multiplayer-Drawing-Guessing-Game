export interface ChatMessage {
  messageId?: string;
  roomId: string;
  playerId: string;
  username: string;
  content: string;
  type: 'USER' | 'SYSTEM';
  createdAt: number;
}
