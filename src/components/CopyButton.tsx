import { copyText } from '../lib/clipboard';
import { useApp } from '../state';

export function CopyButton({
  text,
  label = 'Copy',
  disabled = false,
}: {
  text: string;
  label?: string;
  disabled?: boolean;
}) {
  const { toast } = useApp();
  return (
    <button
      type="button"
      className="btn secondary"
      disabled={disabled || text.length === 0}
      onClick={() => {
        copyText(text)
          .then(() => toast('Copied'))
          .catch(() => toast("Couldn't copy"));
      }}
    >
      {label}
    </button>
  );
}
