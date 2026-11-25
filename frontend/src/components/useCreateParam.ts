import { useEffect } from 'react';
import { useNavigate, useSearch } from '@tanstack/react-router';

/** Opens a create dialog when the page was reached with `?new=1` (home quick actions), then removes the parameter. */
export function useCreateParam(page: 'products' | 'test-methods', open: () => void): void {
  const search = useSearch({ strict: false }) as { new?: string };
  const navigate = useNavigate();
  useEffect(() => {
    if (search.new !== '1') {
      return;
    }
    open();
    if (page === 'products') {
      void navigate({ to: '/products', search: {}, replace: true });
    } else {
      void navigate({ to: '/test-methods', search: {}, replace: true });
    }
  }, [search.new]);
}
