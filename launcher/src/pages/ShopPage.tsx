import { useEffect, useRef, useState } from 'react';
import { api } from '../api';
import { playClick } from '../audio';
import { Modal } from '../components/Modal';
import { formatClock, formatNum } from '../format';
import { useSession } from '../session';
import type { ItemType, Rarity, ShopItem } from '../types';

const RARITY: Record<Rarity, string> = {
  common: '#8d93a0',
  uncommon: '#2f8a4e',
  rare: '#2a6fbe',
  epic: '#7a35b8',
  legendary: '#d08922',
  mythic: '#d2a431',
};

const TYPES: ItemType[] = ['skin', 'emote', 'pickaxe', 'glider'];
const RARITIES: Rarity[] = ['common', 'uncommon', 'rare', 'epic', 'legendary', 'mythic'];
const TYPE_LABEL: Record<ItemType, string> = {
  skin: 'Outfit',
  emote: 'Emote',
  pickaxe: 'Pickaxe',
  glider: 'Glider',
};

function ShopCard({ item, admin, onRemove }: { item: ShopItem; admin: boolean; onRemove: (item: ShopItem) => void }) {
  return (
    <article className={`item-card ${item.section === 'featured' ? 'featured' : 'daily'}`}>
      <div className="item-art" style={{ background: RARITY[item.rarity] }}>
        {admin ? (
          <button type="button" className="item-remove" aria-label={`Remove ${item.name}`} onClick={() => onRemove(item)}>
            Remove
          </button>
        ) : null}
        <img src={item.image} alt="" draggable={false} />
      </div>
      <div className="item-bar">
        <div>
          <strong>{item.name}</strong>
          <small>{TYPE_LABEL[item.type]}</small>
        </div>
        <span className="price">
          {item.vbucks === 0 ? 'Free' : formatNum(item.vbucks)}
          <img className="vbuck-icon" src="/vbucks.png" alt="" draggable={false} />
        </span>
      </div>
    </article>
  );
}

export function ShopPage() {
  const { user, toast } = useSession();
  const [items, setItems] = useState<ShopItem[]>([]);
  const [seconds, setSeconds] = useState<number | null>(null);
  const [adding, setAdding] = useState(false);
  const [name, setName] = useState('');
  const [type, setType] = useState<ItemType>('skin');
  const [rarity, setRarity] = useState<Rarity>('epic');
  const [section, setSection] = useState<'featured' | 'daily'>('featured');
  const [vbucks, setVbucks] = useState('800');
  const [image, setImage] = useState('');
  const [formError, setFormError] = useState('');
  const left = useRef(0);
  const ready = useRef(false);
  const admin = user?.role === 'admin';

  async function loadItems() {
    const data = await api<{ items: ShopItem[] }>('/shop/items');
    setItems(data.items);
  }

  useEffect(() => {
    let alive = true;
    async function loadClock() {
      const data = await api<{ seconds: number }>('/shop/refresh');
      if (!alive) return;
      left.current = data.seconds;
      ready.current = true;
      setSeconds(data.seconds);
    }
    loadItems().catch((err: Error) => toast(err.message));
    loadClock().catch((err: Error) => toast(err.message));
    const timer = window.setInterval(() => {
      if (!ready.current) return;
      left.current -= 1;
      if (left.current <= 0) {
        ready.current = false;
        loadClock().catch(() => undefined);
      } else {
        setSeconds(left.current);
      }
    }, 1000);
    return () => {
      alive = false;
      window.clearInterval(timer);
    };
  }, [toast]);

  function onFile(file: File | undefined) {
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => setImage(String(reader.result || ''));
    reader.readAsDataURL(file);
  }

  async function removeItem(item: ShopItem) {
    playClick();
    try {
      const data = await api<{ items: ShopItem[] }>(`/shop/items/${item.id}`, { method: 'DELETE' });
      setItems(data.items);
      toast(`Removed ${item.name}`);
    } catch (err) {
      toast(err instanceof Error ? err.message : 'Could not remove that item.');
    }
  }

  async function addItem(event: React.FormEvent) {
    event.preventDefault();
    playClick();
    setFormError('');
    if (!image.startsWith('data:image/')) {
      setFormError('Import an image for this item.');
      return;
    }
    try {
      await api('/shop/items', {
        method: 'POST',
        body: { name, type, rarity, vbucks: Number(vbucks), image, section },
      });
      setAdding(false);
      setName('');
      setImage('');
      setVbucks('800');
      setSection('featured');
      await loadItems();
      toast('Item added');
    } catch (err) {
      setFormError(err instanceof Error ? err.message : 'Could not add that item.');
    }
  }

  const featured = items.filter((item) => item.section === 'featured');
  const daily = items.filter((item) => item.section !== 'featured');

  return (
    <div className="page shop">
      <header className="page-head">
        <div>
          <h1>Item Shop</h1>
          <p>Original cosmetics for your locker.</p>
        </div>
        <div className="head-actions">
          {admin ? (
            <button
              type="button"
              className="btn ghost"
              onClick={() => {
                playClick();
                setFormError('');
                setAdding(true);
              }}
            >
              Add item
            </button>
          ) : null}
          <div className="refresh-chip" aria-live="polite">
            Refreshes in <span>{seconds === null ? '--:--:--' : formatClock(seconds)}</span>
          </div>
        </div>
      </header>

      <section className="shop-block">
        <h2>Featured</h2>
        <div className="shop-row">
          {featured.map((item) => (
            <ShopCard key={item.id} item={item} admin={admin} onRemove={removeItem} />
          ))}
        </div>
      </section>

      <section className="shop-block">
        <h2>Daily</h2>
        <div className="shop-grid">
          {daily.map((item) => (
            <ShopCard key={item.id} item={{ ...item, section: 'daily' }} admin={admin} onRemove={removeItem} />
          ))}
        </div>
      </section>

      {adding ? (
        <Modal title="Add item" onClose={() => setAdding(false)}>
          <form className="stack" onSubmit={addItem}>
            <label>
              Name
              <input value={name} onChange={(event) => setName(event.target.value)} maxLength={32} required />
            </label>
            <div className="split">
              <label>
                Type
                <select value={type} onChange={(event) => setType(event.target.value as ItemType)}>
                  {TYPES.map((entry) => (
                    <option key={entry} value={entry}>
                      {TYPE_LABEL[entry]}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                Rarity
                <select value={rarity} onChange={(event) => setRarity(event.target.value as Rarity)}>
                  {RARITIES.map((entry) => (
                    <option key={entry} value={entry}>
                      {entry}
                    </option>
                  ))}
                </select>
              </label>
            </div>
            <div className="split">
              <label>
                V-Bucks
                <input
                  type="number"
                  min={0}
                  max={100000}
                  value={vbucks}
                  onChange={(event) => setVbucks(event.target.value)}
                  required
                />
              </label>
              <label>
                Placement
                <select value={section} onChange={(event) => setSection(event.target.value as 'featured' | 'daily')}>
                  <option value="featured">Featured</option>
                  <option value="daily">Daily</option>
                </select>
              </label>
            </div>
            <label>
              Image
              <input type="file" accept="image/*" onChange={(event) => onFile(event.target.files?.[0])} />
            </label>
            {image ? (
              <article className="item-card preview-card">
                <div className="item-art" style={{ background: RARITY[rarity] }}>
                  <img src={image} alt="" draggable={false} />
                </div>
                <div className="item-bar">
                  <div>
                    <strong>{name || 'Name'}</strong>
                    <small>{TYPE_LABEL[type]}</small>
                  </div>
                  <span className="price">
                    {formatNum(Number(vbucks) || 0)}
                    <img className="vbuck-icon" src="/vbucks.png" alt="" draggable={false} />
                  </span>
                </div>
              </article>
            ) : null}
            {formError ? <p className="form-error">{formError}</p> : null}
            <button type="submit" className="btn primary">
              Save item
            </button>
          </form>
        </Modal>
      ) : null}
    </div>
  );
}
