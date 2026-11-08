import { describe, it, expect, beforeEach } from 'vitest';
import { roomStore } from './roomStore';

describe('roomStore', () => {
  beforeEach(() => {
    roomStore.clearRoom();
  });

  it('sets room and persists roomId to localStorage', () => {
    roomStore.setRoom({
      roomId: 'ROOM99',
      name: 'Test Room',
      status: 'PLAYING',
      hostPlayerId: 'player-1',
      players: [{ playerId: 'player-1', username: 'Host' }],
      maxPlayers: 4,
      roundCount: 3,
      roundDuration: 60,
      playerCount: 1,
      selectedCategories: ['ANIMALS'],
    });

    const state = roomStore.getState();
    expect(state.isInRoom).toBe(true);
    expect(state.room?.roomId).toBe('ROOM99');
    expect(roomStore.getLastRoomId()).toBe('ROOM99');
    expect(state.isRestoring).toBe(false);
  });

  it('manages restoring state and errors', () => {
    roomStore.setIsRestoring(true);
    expect(roomStore.getState().isRestoring).toBe(true);

    roomStore.setRestoreError('Phòng không tồn tại');
    expect(roomStore.getState().restoreError).toBe('Phòng không tồn tại');

    roomStore.clearRoom();
    expect(roomStore.getState().isInRoom).toBe(false);
    expect(roomStore.getState().isRestoring).toBe(false);
    expect(roomStore.getState().restoreError).toBeNull();
    expect(roomStore.getLastRoomId()).toBeNull();
  });

  it('supports explicit setLastRoomId from URL', () => {
    roomStore.setLastRoomId('URL123');
    expect(roomStore.getLastRoomId()).toBe('URL123');
  });
});
