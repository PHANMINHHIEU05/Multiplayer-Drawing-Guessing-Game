import React, { useCallback, useEffect, useRef, useState } from "react";
import { wsClient } from "../../websocket/WebSocketClient";
import { MessageType } from "../../websocket/protocol";
import { usePings } from "../../store/pingStore";

const PING_COOLDOWN_MS = 400;

/**
 * Double-click hit-area over the canvas for guessers: double-clicking drops
 * the LoL-style "?" ping at that spot for the whole room. Rendered only while
 * enabled, so drawer interaction and phase overlays fall through untouched.
 */
export const PingClickLayer: React.FC<{ enabled: boolean }> = ({ enabled }) => {
  const cooldownUntilRef = useRef(0);
  const [isTouch, setIsTouch] = useState(false);

  useEffect(() => {
    if (typeof window.matchMedia === "function") {
      setIsTouch(window.matchMedia("(pointer: coarse)").matches);
    }
  }, []);

  const handleDoubleClick = useCallback(
    (event: React.MouseEvent<HTMLDivElement>) => {
      if (Date.now() < cooldownUntilRef.current) return;
      const rect = event.currentTarget.getBoundingClientRect();
      if (rect.width <= 0 || rect.height <= 0) return;

      const x = Math.min(
        1,
        Math.max(0, (event.clientX - rect.left) / rect.width),
      );
      const y = Math.min(
        1,
        Math.max(0, (event.clientY - rect.top) / rect.height),
      );

      cooldownUntilRef.current = Date.now() + PING_COOLDOWN_MS;
      wsClient
        .send(MessageType.SEND_PING, { x, y, pingType: "MISSING" }, 3000)
        .catch(() => {});
    },
    [],
  );

  if (!enabled) return null;

  return (
    <div
      aria-hidden="true"
      className="absolute inset-0 z-10 cursor-pointer touch-none select-none"
      onDoubleClick={handleDoubleClick}
      onContextMenu={(event) => event.preventDefault()}
    >
      <span className="dg-ping-hint pointer-events-none absolute right-3 top-3 z-10 rounded-full bg-slate-950/60 px-3 py-1 text-[11px] font-semibold text-white/90 shadow-sm">
        {isTouch ? "Chạm 2 lần để ping ❓" : "Nhấn 2 lần chuột để ping ❓"}
      </span>
    </div>
  );
};

/**
 * Renders the ephemeral "?" markers anchored to their normalized canvas
 * coordinates: just the big golden "?" with an expanding golden ring — no
 * badge, no background. Subscribes to pingStore here (not in GamePage) so
 * incoming pings never re-render the rest of the game page.
 */
export const PingMarkerLayer: React.FC<{
  gameId?: string;
  roundNumber: number;
}> = ({ gameId, roundNumber }) => {
  const pings = usePings();
  const visible = pings.filter(
    (ping) => ping.gameId === gameId && ping.roundNumber === roundNumber,
  );

  return (
    <div className="pointer-events-none absolute inset-0 z-20 overflow-hidden">
      {visible.map((ping) => (
        <div
          key={ping.id}
          className="absolute"
          style={{ left: `${ping.x * 100}%`, top: `${ping.y * 100}%` }}
        >
          <div className="dg-ping-drop relative h-12 w-12">
            {/* Expanding golden ring */}
            <span className="dg-ping-ring absolute inset-0 m-auto h-12 w-12 rounded-full border-[3px] border-[#e8b93a]" />
            {/* The "?" itself — gradient gold, dark outline for legibility */}
            <span className="absolute inset-0 flex items-center justify-center bg-gradient-to-b from-[#ffe27a] to-[#e0a91e] bg-clip-text text-[44px] font-black leading-none text-transparent drop-shadow-[0_2px_3px_rgba(0,0,0,0.55)]">
              ?
            </span>
            {/* Who pinged */}
            <span className="absolute left-1/2 top-full mt-0.5 -translate-x-1/2 whitespace-nowrap rounded-full border border-amber-200 bg-white/90 px-2 py-0.5 text-center text-[10px] font-bold text-slate-800 shadow-sm">
              {ping.displayName}
            </span>
          </div>
        </div>
      ))}
    </div>
  );
};
