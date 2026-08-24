import React, { useEffect, useRef, useState } from 'react';

interface PaintSplashOverlayProps {
  forcePlay?: boolean;
  onComplete?: () => void;
}

interface Particle {
  x: number;
  y: number;
  vx: number;
  vy: number;
  radius: number;
  color: string;
  alpha: number;
}

export const PaintSplashOverlay: React.FC<PaintSplashOverlayProps> = ({ forcePlay = false, onComplete }) => {
  const [isVisible, setIsVisible] = useState(true);
  const [isFading, setIsFading] = useState(false);
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const onCompleteRef = useRef(onComplete);
  onCompleteRef.current = onComplete;

  useEffect(() => {
    if (forcePlay) {
      setIsVisible(true);
      setIsFading(false);
    }
  }, [forcePlay]);

  useEffect(() => {
    if (!isVisible) return;

    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    let width = (canvas.width = window.innerWidth);
    let height = (canvas.height = window.innerHeight);

    const handleResize = () => {
      if (!canvas) return;
      width = canvas.width = window.innerWidth;
      height = canvas.height = window.innerHeight;
    };
    window.addEventListener('resize', handleResize);

    const startTime = performance.now();
    let animationFrameId: number;
    const particles: Particle[] = [];

    // Colors
    const COLORS = ['#1E88E5', '#7C3AED', '#FBBF24', '#EC4899', '#22D3EE', '#10B981'];

    // Spawn splash particles
    const spawnSplatter = (originX: number, originY: number, count = 80) => {
      for (let i = 0; i < count; i++) {
        const angle = Math.random() * Math.PI * 2;
        const speed = Math.random() * 18 + 5;
        particles.push({
          x: originX,
          y: originY,
          vx: Math.cos(angle) * speed,
          vy: Math.sin(angle) * speed - Math.random() * 8, // slight upward bias
          radius: Math.random() * 24 + 8,
          color: COLORS[Math.floor(Math.random() * COLORS.length)],
          alpha: 1,
        });
      }
    };

    let splashTriggered = false;
    let notifiedComplete = false;

    // Main Animation Loop
    const render = (now: number) => {
      const elapsed = (now - startTime) / 1000; // in seconds

      ctx.clearRect(0, 0, width, height);

      // 1. Dark backdrop initial
      ctx.fillStyle = '#0F172A';
      ctx.fillRect(0, 0, width, height);

      const groundY = height * 0.65;
      const bucketX = width * 0.58;
      const bucketY = groundY;

      // ─────────────────────────────────────────────────────────────
      // Phase 1: Brush Character Runs In (0.0s -> 1.2s)
      // ─────────────────────────────────────────────────────────────
      const brushStartX = -120;
      const brushTargetX = bucketX - 90;
      let brushX = brushStartX;
      let brushY = groundY;
      let brushRotation = 0;
      let legKick = 0;

      if (elapsed < 1.0) {
        const t = Math.min(1, elapsed / 1.0);
        // Ease out quad
        brushX = brushStartX + (brushTargetX - brushStartX) * (1 - (1 - t) * (1 - t));
        brushY = groundY - Math.abs(Math.sin(elapsed * 12)) * 30; // Bouncy run
        brushRotation = Math.sin(elapsed * 12) * 0.15;
      } else if (elapsed < 1.3) {
        // Windup and Kick
        brushX = brushTargetX;
        brushY = groundY;
        const kickProgress = (elapsed - 1.0) / 0.3;
        if (kickProgress < 0.4) {
          // Windup back
          brushRotation = -0.25;
          legKick = -0.3;
        } else {
          // KICK FORWARD!
          brushRotation = 0.4;
          legKick = 0.8;
          if (!splashTriggered) {
            splashTriggered = true;
            spawnSplatter(bucketX + 20, bucketY - 30, 120);
          }
        }
      } else {
        // Brush celebrates / watches the paint wave
        brushX = brushTargetX;
        brushY = groundY - Math.abs(Math.sin((elapsed - 1.3) * 8)) * 15;
        brushRotation = 0.1;
      }

      // ─────────────────────────────────────────────────────────────
      // Phase 2: Paint Bucket Physics (0.0s -> 3.5s)
      // ─────────────────────────────────────────────────────────────
      let bucketAngle = 0;
      let bucketOffsetX = 0;
      let bucketOffsetY = 0;

      if (elapsed >= 1.15) {
        const fallT = Math.min(1, (elapsed - 1.15) / 0.4);
        bucketAngle = fallT * (Math.PI / 2); // Tilt to 90 degrees
        bucketOffsetX = fallT * 60;
        bucketOffsetY = fallT * 10;
      }

      // ─────────────────────────────────────────────────────────────
      // Phase 3: Paint Flood & Splash Spread (1.2s -> 3.5s)
      // ─────────────────────────────────────────────────────────────
      if (elapsed >= 1.2) {
        const floodT = Math.min(1, (elapsed - 1.2) / 1.5);
        const maxR = Math.max(width, height) * 1.4;
        const currentR = floodT * maxR;

        // Gradient multi-wave liquid expanding from spilled bucket
        const originX = bucketX + bucketOffsetX;
        const originY = bucketY + bucketOffsetY;

        // Wave 1: Violet
        ctx.fillStyle = '#7C3AED';
        ctx.beginPath();
        ctx.arc(originX, originY, currentR, 0, Math.PI * 2);
        ctx.fill();

        // Wave 2: Sky Blue
        if (floodT > 0.12) {
          ctx.fillStyle = '#1E88E5';
          ctx.beginPath();
          ctx.arc(originX, originY, (floodT - 0.12) * maxR * 1.05, 0, Math.PI * 2);
          ctx.fill();
        }

        // Wave 3: Yellow/Amber
        if (floodT > 0.25) {
          ctx.fillStyle = '#FBBF24';
          ctx.beginPath();
          ctx.arc(originX, originY, (floodT - 0.25) * maxR * 0.8, 0, Math.PI * 2);
          ctx.fill();
        }

        // Wave 4: Pink
        if (floodT > 0.4) {
          ctx.fillStyle = '#EC4899';
          ctx.beginPath();
          ctx.arc(originX, originY, (floodT - 0.4) * maxR * 0.5, 0, Math.PI * 2);
          ctx.fill();
        }

        // Final Wave: Dopamine Core Sky Blue (#1E88E5)
        if (floodT > 0.55) {
          ctx.fillStyle = '#1E88E5';
          ctx.beginPath();
          ctx.arc(originX, originY, (floodT - 0.55) * maxR * 1.2, 0, Math.PI * 2);
          ctx.fill();
        }
      }

      // Draw Splatter Particles
      for (let i = particles.length - 1; i >= 0; i--) {
        const p = particles[i];
        p.x += p.vx;
        p.y += p.vy;
        p.vy += 0.4; // gravity
        p.vx *= 0.98;

        ctx.fillStyle = p.color;
        ctx.beginPath();
        ctx.arc(p.x, p.y, p.radius, 0, Math.PI * 2);
        ctx.fill();

        // Little droplet tail
        ctx.beginPath();
        ctx.arc(p.x - p.vx * 1.5, p.y - p.vy * 1.5, p.radius * 0.6, 0, Math.PI * 2);
        ctx.fill();
      }

      // ─────────────────────────────────────────────────────────────
      // Draw 3D Cartoon Paint Bucket
      // ─────────────────────────────────────────────────────────────
      ctx.save();
      ctx.translate(bucketX + bucketOffsetX, bucketY + bucketOffsetY);
      ctx.rotate(bucketAngle);

      // Bucket shadow
      ctx.fillStyle = 'rgba(0,0,0,0.3)';
      ctx.beginPath();
      ctx.ellipse(0, 10, 40, 12, 0, 0, Math.PI * 2);
      ctx.fill();

      // Bucket Body
      ctx.fillStyle = '#CBD5E1';
      ctx.beginPath();
      ctx.moveTo(-35, -50);
      ctx.lineTo(35, -50);
      ctx.lineTo(28, 10);
      ctx.lineTo(-28, 10);
      ctx.closePath();
      ctx.fill();

      // Bucket Rim Top
      ctx.fillStyle = '#94A3B8';
      ctx.beginPath();
      ctx.ellipse(0, -50, 35, 12, 0, 0, Math.PI * 2);
      ctx.fill();

      // Paint Inside Bucket (Yellow & Blue)
      ctx.fillStyle = '#FBBF24';
      ctx.beginPath();
      ctx.ellipse(0, -50, 31, 10, 0, 0, Math.PI * 2);
      ctx.fill();

      // Bucket Metallic Band
      ctx.fillStyle = '#64748B';
      ctx.fillRect(-30, -25, 60, 8);

      // Bucket Handle
      ctx.strokeStyle = '#475569';
      ctx.lineWidth = 4;
      ctx.beginPath();
      ctx.arc(0, -45, 38, Math.PI, 0);
      ctx.stroke();

      ctx.restore();

      // ─────────────────────────────────────────────────────────────
      // Draw Stylized Cartoon Paintbrush Character
      // ─────────────────────────────────────────────────────────────
      ctx.save();
      ctx.translate(brushX, brushY);
      ctx.rotate(brushRotation);

      // Character shadow
      ctx.fillStyle = 'rgba(0,0,0,0.3)';
      ctx.beginPath();
      ctx.ellipse(0, 8, 25, 8, 0, 0, Math.PI * 2);
      ctx.fill();

      // Wooden Handle (Body)
      ctx.fillStyle = '#D97706';
      ctx.beginPath();
      ctx.moveTo(-12, -70);
      ctx.quadraticCurveTo(-18, -30, -14, -10);
      ctx.lineTo(14, -10);
      ctx.quadraticCurveTo(18, -30, 12, -70);
      ctx.quadraticCurveTo(0, -85, -12, -70);
      ctx.closePath();
      ctx.fill();

      // Chrome Ferrule
      ctx.fillStyle = '#E2E8F0';
      ctx.beginPath();
      ctx.rect(-15, -10, 30, 18);
      ctx.fill();
      ctx.fillStyle = '#94A3B8';
      ctx.fillRect(-15, -4, 30, 3);

      // Bristles (Fluffy Hair with Purple/Cyan Paint Tip)
      ctx.fillStyle = '#7C3AED';
      ctx.beginPath();
      ctx.moveTo(-14, 8);
      ctx.quadraticCurveTo(-20, 25, -10, 42);
      ctx.quadraticCurveTo(0, 52, 10, 42);
      ctx.quadraticCurveTo(20, 25, 14, 8);
      ctx.closePath();
      ctx.fill();

      // Glowing paint tip
      ctx.fillStyle = '#22D3EE';
      ctx.beginPath();
      ctx.arc(0, 38, 8, 0, Math.PI * 2);
      ctx.fill();

      // Cute Big Cartoon Eyes
      ctx.fillStyle = '#FFFFFF';
      ctx.beginPath();
      ctx.ellipse(-6, -42, 6, 8, 0, 0, Math.PI * 2);
      ctx.ellipse(6, -42, 6, 8, 0, 0, Math.PI * 2);
      ctx.fill();

      // Pupils (Looking towards bucket)
      ctx.fillStyle = '#0F172A';
      ctx.beginPath();
      ctx.arc(-4, -42, 3.5, 0, Math.PI * 2);
      ctx.arc(8, -42, 3.5, 0, Math.PI * 2);
      ctx.fill();

      // Eye Sparkle
      ctx.fillStyle = '#FFFFFF';
      ctx.beginPath();
      ctx.arc(-5, -44, 1.5, 0, Math.PI * 2);
      ctx.arc(7, -44, 1.5, 0, Math.PI * 2);
      ctx.fill();

      // Cute Smile
      ctx.strokeStyle = '#0F172A';
      ctx.lineWidth = 2.5;
      ctx.beginPath();
      ctx.arc(0, -32, 5, 0.2, Math.PI - 0.2);
      ctx.stroke();

      // Cartoon Kicking Leg
      ctx.fillStyle = '#B45309';
      ctx.beginPath();
      ctx.rect(4, 5, 8, 14);
      ctx.fill();
      // Kicking Foot
      ctx.fillStyle = '#EF4444';
      ctx.beginPath();
      ctx.ellipse(8 + legKick * 15, 19, 9, 6, legKick * 0.4, 0, Math.PI * 2);
      ctx.fill();

      ctx.restore();

      // ─────────────────────────────────────────────────────────────
      // Phase 4: Big "Dopamine.io" Splash Logo Reveal (2.0s -> 3.2s)
      // ─────────────────────────────────────────────────────────────
      if (elapsed >= 2.0) {
        const logoT = Math.min(1, (elapsed - 2.0) / 0.8);
        const scale = 0.5 + Math.sin(logoT * Math.PI * 0.5) * 0.5;

        ctx.save();
        ctx.translate(width / 2, height / 2 - 20);
        ctx.scale(scale, scale);
        ctx.font = '800 68px "Bricolage Grotesque", sans-serif';
        ctx.textAlign = 'center';

        // 3D Shadow
        ctx.fillStyle = '#0F172A';
        ctx.fillText('🎨 Dopamine.io ⚡', 0, 8);

        // Vibrant Gradient Text
        const textGrad = ctx.createLinearGradient(0, -40, 0, 20);
        textGrad.addColorStop(0, '#FDE047');
        textGrad.addColorStop(0.5, '#F59E0B');
        textGrad.addColorStop(1, '#EF4444');
        ctx.fillStyle = textGrad;
        ctx.fillText('🎨 Dopamine.io ⚡', 0, 0);

        ctx.font = '700 22px "Plus Jakarta Sans", sans-serif';
        ctx.fillStyle = '#FFFFFF';
        ctx.shadowColor = 'rgba(0,0,0,0.4)';
        ctx.shadowBlur = 8;
        ctx.fillText('Thỏa sức vẽ & đoán từ cực vui!', 0, 48);

        ctx.restore();
      }

      // Notify completion once (~2.3s) so Page 1 pops in as paint floods screen
      if (elapsed >= 2.3 && !notifiedComplete) {
        notifiedComplete = true;
        onCompleteRef.current?.();
      }

      // Finish condition (after 3.2s)
      if (elapsed < 3.2) {
        animationFrameId = requestAnimationFrame(render);
      } else {
        setIsFading(true);
        setTimeout(() => {
          setIsVisible(false);
          if (!notifiedComplete) {
            notifiedComplete = true;
            onCompleteRef.current?.();
          }
        }, 500);
      }
    };

    animationFrameId = requestAnimationFrame(render);

    return () => {
      cancelAnimationFrame(animationFrameId);
      window.removeEventListener('resize', handleResize);
    };
  }, [isVisible]);

  if (!isVisible) return null;

  const handleSkip = () => {
    setIsFading(true);
    onCompleteRef.current?.();
    setTimeout(() => {
      setIsVisible(false);
    }, 200);
  };

  return (
    <div
      className={`fixed inset-0 z-[9999] flex items-center justify-center bg-slate-900 transition-opacity duration-600 select-none ${
        isFading ? 'opacity-0 pointer-events-none' : 'opacity-100'
      }`}
    >
      <canvas ref={canvasRef} className="w-full h-full block" />
      <button
        onClick={handleSkip}
        className="absolute bottom-8 text-white/90 text-xs font-black bg-white/20 hover:bg-white/30 border-2 border-white/40 px-5 py-2.5 rounded-full transition-all backdrop-blur-md shadow-2xl hover:scale-105"
      >
        Bỏ qua Intro ⏩
      </button>
    </div>
  );
};
