/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,ts,jsx,tsx}'],
  theme: {
    extend: {
      colors: {
        primary: {
          DEFAULT: '#1E88E5',
          dark: '#1565C0',
          light: '#64B5F6',
          container: '#0077ce',
        },
        secondary: {
          DEFAULT: '#7C3AED',
          dark: '#5B21B6',
          light: '#A78BFA',
          container: '#8a4cfc',
        },
        tertiary: {
          DEFAULT: '#FBBF24',
          dark: '#D97706',
          light: '#FDE68A',
        },
        accent: {
          pink: '#EC4899',
          cyan: '#22D3EE',
          green: '#10B981',
          orange: '#F97316',
        },
      },
      fontFamily: {
        headline: ['"Bricolage Grotesque"', 'sans-serif'],
        sans: ['"Plus Jakarta Sans"', 'sans-serif'],
        mono: ['"JetBrains Mono"', 'monospace'],
      },
      boxShadow: {
        '3d-primary': '0 4px 0 0 #1565C0',
        '3d-secondary': '0 4px 0 0 #5B21B6',
        '3d-emerald': '0 4px 0 0 #059669',
        '3d-amber': '0 4px 0 0 #D97706',
      },
    },
  },
  plugins: [],
};
