# E-commerce Frontend

Project overview
----------------
Simple e-commerce frontend app that lets users browse products from FakeStoreAPI, view product details, and manage a shopping cart. Built with React + TypeScript, Vite, plain Redux, TanStack Query and React Router. Focuses on accessibility and mobile responsiveness.

Local development server
------------------------
The development server runs on port 3000.

Setup
-----
1. Install dependencies:

```bash
npm install
```

2. Start development server (serves at http://localhost:3000):

```bash
npm run start
```

Build & preview
---------------
Build for production:

```bash
npm run build
```

Preview production build locally:

```bash
npm run preview
```

Notes
-----
- Uses plain Redux for cart state persisted to `localStorage`.
- Uses TanStack Query for data fetching from FakeStoreAPI.
- Client-side pagination (12 items per page).
- Plain CSS for styles.
- Entry points and key files:
	- `src/pages/ProductListing/Container.tsx`
	- `src/pages/ProductDetails/Container.tsx`
	- `src/pages/Cart/Container.tsx`
	- `src/components/*` (UI components)
	- `src/store/*` (Redux store)
	- `src/api/fakeStore.ts` (API wrappers)

# FE-Assignment-1
E-commerce website
