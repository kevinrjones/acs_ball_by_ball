/** @type {import('tailwindcss').Config} */
module.exports = {
  content: [
    "./src/**/*.{html,ts}",
  ],
  theme: {
    extend: {
      colors: {
        brand: {
          50: '#f2fbf6',
          100: '#e1f6eb',
          500: '#15803d',
          600: '#0f6832',
          800: '#144129',
          900: '#0c2719',
        },
        slate: {
          850: '#172033',
          900: '#0f172a',
        }
      },
      fontFamily: {
        sans: ['Inter', 'system-ui', '-apple-system', 'BlinkMacSystemFont', 'Segoe UI', 'Roboto', 'sans-serif'],
      }
    },
  },
  plugins: [],
};
