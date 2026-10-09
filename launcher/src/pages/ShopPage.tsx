import { useEffect, useRef, useState } from 'react';
import { api } from '../api';
import { playClick } from '../audio';
import { IconType, VBuck } from '../components/Icons';
import { Modal } from '../components/Modal';
import { formatClock, formatNum } from '../format';
import { useSession } from '../session';
import type { ItemType, Rarity, ShopItem } from '../types';

const RARITY: Record<Rarity, [string, string]> = {
  common: ['#c5cad3', '#3e4450'],
  uncommon: ['#8ee7a8', '#146b38'],
  rare: ['#8ec8ff', '#154e9e'],
  epic: ['#e0b0ff', '#5b21b6'],
  legendary: ['#ffc27a', '#b45309'],
  mythic: ['#ffe7a3', '#a16207'],
};

const TYPES: ItemType[] = ['skin', 'emote', 'pickaxe', 'glider'];
const RARITIES: Rarity[] = ['common', 'uncommon', 'rare', 'epic', 'legendary', 'mythic'];

export function ShopPage() {
  const { user, toast, refresh } = useSession();
  const [items, setItems] = useState<ShopItem[]>([]);
  const [seconds, setSeconds] = useState<number | null>(null);
  const [adding, setAdding] = useState(false);
  const [name, setName] = useState('');
  const [type, setType] = useState<ItemType>('skin');
  const [rarity, setRarity] = useState<Rarity>('epic');
  const [vbucks, setVbucks] = useState('800');
  const [image, setImage] = useState('');
  const [formError, setFormError] = useState('');
  const left = useRef(0);
  const ready = useRef(false);

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

  async function equip(item: ShopItem) {
    playClick();
    try {
      await api('/shop/equip', { method: 'POST', body: { itemId: item.id } });
      await refresh();
      toast(`Equipped ${item.name}`);
    } catch (err) {
      toast(err instanceof Error ? err.message : 'Could not equip that item.');
    }
  }

  function onFile(file: File | undefined) {
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => setImage(String(reader.result || ''));
    reader.readAsDataURL(file);
  }

  async function addItem(event: React.FormEvent) {
    event.preventDefault();
    playClick();
    setFormError('');
    try {
      await api('/shop/items', {
        method: 'POST',
        body: { name, type, rarity, vbucks: Number(vbucks), image },
      });
      setAdding(false);
      setName('');
      setImage('');
      setVbucks('800');
      await loadItems();
      toast('Item added');
    } catch (err) {
      setFormError(err instanceof Error ? err.message : 'Could not add that item.');
    }
  }

  const equippedIds = new Set(
    [user?.equipped.skin?.id, user?.equipped.emote?.id, user?.equipped.pickaxe?.id, user?.equipped.glider?.id].filter(Boolean),
  );

  return (
    <div className="page shop">
      <header className="page-head">
        <div>
          <h1>Item Shop</h1>
          <p>Original cosmetics for your locker. Equip one to pin it on Home.</p>
        </div>
        <div className="head-actions">
          {user?.role === 'admin' ? (
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

      <div className="shop-grid">
        {items.map((item) => {
          const [top, bottom] = RARITY[item.rarity];
          return (
            <button
              key={item.id}
              type="button"
              className={equippedIds.has(item.id) ? 'item-card equipped' : 'item-card'}
              style={{ background: `linear-gradient(180deg, ${top} 0%, ${bottom} 78%)` }}
              onClick={() => equip(item)}
            >
              <span className="type-badge">
                <IconType type={item.type} />
              </span>
              <img src={item.image} alt="" />
              <span className="item-meta">
                <span className="item-name">{item.name}</span>
                {item.vbucks === 0 ? (
                  <span className="price free">Free</span>
                ) : (
                  <span className="price">
                    {formatNum(item.vbucks)} <VBuck size={15} />
                  </span>
                )}
              </span>
            </button>
          );
        })}
      </div>

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
                      {entry}
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
              Image URL
              <input
                value={image.startsWith('data:') ? '' : image}
                onChange={(event) => setImage(event.target.value)}
                placeholder="https://… or upload below"
              />
            </label>
            <label>
              Upload
              <input type="file" accept="image/*" onChange={(event) => onFile(event.target.files?.[0])} />
            </label>
            {image ? <img className="upload-preview" src={image} alt="" /> : null}
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
