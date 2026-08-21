import { describe, it, expect } from 'vitest';
import { playerStore } from './playerStore';

describe('playerStore', () => {
  it('updates username and persists to localStorage', () => {
    playerStore.setPlayer('Alice', 'player-alice');
    const state = playerStore.getState();
    expect(state.username).toBe('Alice');
    expect(state.playerId).toBe('player-alice');
    expect(localStorage.getItem('app_username')).toBe('Alice');
    expect(localStorage.getItem('app_player_id')).toBe('player-alice');
  });
});
