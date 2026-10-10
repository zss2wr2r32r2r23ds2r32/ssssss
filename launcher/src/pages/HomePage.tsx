import { useEffect, useState } from 'react';
import { api } from '../api';
import { playClick } from '../audio';
import { Modal } from '../components/Modal';
import { formatNum } from '../format';
import { useSession } from '../session';
import type { Build, NewsItem, Stats } from '../types';

export function HomePage() {
  const { user, toast } = useSession();
  const [stats, setStats] = useState<Stats | null>(null);
  const [news, setNews] = useState<NewsItem[] | null>(null);
  const [selectedName, setSelectedName] = useState<string | null>(null);
  const [adding, setAdding] = useState(false);
  const [title, setTitle] = useState('');
  const [body, setBody] = useState('');
  const [image, setImage] = useState('');
  const [newsError, setNewsError] = useState('');

  async function load() {
    const [nextStats, nextNews, builds] = await Promise.all([
      api<Stats>('/stats'),
      api<{ news: NewsItem[] }>('/news'),
      api<{ builds: Build[]; selectedId: string | null }>('/builds'),
    ]);
    setStats(nextStats);
    setNews(nextNews.news);
    const selected = builds.builds.find((build) => build.id === builds.selectedId);
    setSelectedName(selected?.name ?? null);
  }

  useEffect(() => {
    load().catch((err: Error) => toast(err.message));
  }, [toast, user?.displayName]);

  async function removeNews(id: string) {
    playClick();
    try {
      const data = await api<{ news: NewsItem[] }>(`/news/${id}`, { method: 'DELETE' });
      setNews(data.news);
    } catch (err) {
      toast(err instanceof Error ? err.message : 'Could not remove that post.');
    }
  }

  async function launch() {
    playClick();
    try {
      const result = await api<{ name: string }>('/builds/launch', { method: 'POST', body: {} });
      toast(`Starting ${result.name}…`);
    } catch (err) {
      toast(err instanceof Error ? err.message : 'No build is selected.');
    }
  }

  async function addNews(event: React.FormEvent) {
    event.preventDefault();
    playClick();
    setNewsError('');
    try {
      await api('/news', { method: 'POST', body: { title, body, image } });
      setTitle('');
      setBody('');
      setImage('');
      setAdding(false);
      await load();
      toast('News posted');
    } catch (err) {
      setNewsError(err instanceof Error ? err.message : 'Could not post news.');
    }
  }

  return (
    <div className="page home">
      <section className="hero">
        <div className="hero-skin">
          <img src="/skin-default.png" alt="Default skin" draggable={false} />
        </div>
        <div className="hero-copy">
          <h1>Welcome Back, {user?.discordName}!</h1>
          <p>Experience Chapter 2 Season 2 With Nexa</p>
        </div>
      </section>

      <section className="home-grid">
        <article className="card stat-card">
          <header className="card-head">
            <h2>Statistics</h2>
          </header>
          <div className="stat-list">
            <Stat label="Eliminations" value={stats ? formatNum(stats.elims) : '—'} />
            <Stat label="Victory Royals" value={stats ? formatNum(stats.wins) : '—'} />
            <Stat label="Matches Played" value={stats ? formatNum(stats.matches) : '—'} />
            <Stat label="V-Bucks" value={stats ? formatNum(stats.vbucks) : '—'} />
          </div>
        </article>

        <article className="card news-card">
          <header className="card-head">
            <h2>News</h2>
            {user?.role === 'admin' ? (
              <button
                type="button"
                className="text-btn"
                onClick={() => {
                  playClick();
                  setAdding(true);
                }}
              >
                Add news
              </button>
            ) : null}
          </header>
          {!news || news.length === 0 ? (
            <div className="quiet">
              <p>No news yet</p>
              <span>Posts from an admin will land here.</span>
            </div>
          ) : (
            <ul className="news-list">
              {news.map((item) => (
                <li key={item.id}>
                  <div>
                    <strong>{item.title}</strong>
                    <p>{item.body}</p>
                    {item.image ? <img className="news-image" src={item.image} alt="" draggable={false} /> : null}
                  </div>
                  <div className="news-side">
                    {user?.role === 'admin' ? (
                      <button type="button" className="text-btn" onClick={() => removeNews(item.id)}>
                        Remove
                      </button>
                    ) : null}
                    <time dateTime={item.createdAt}>
                      {new Date(item.createdAt).toLocaleDateString(undefined, { month: 'short', day: 'numeric' })}
                    </time>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </article>
      </section>

      <div className="launch-wrap">
        <p className="launch-meta">{selectedName ? `Selected build · ${selectedName}` : 'No build selected'}</p>
        <button type="button" className="launch" onClick={launch}>
          Launch Fortnite
        </button>
      </div>

      {adding ? (
        <Modal title="Add news" onClose={() => setAdding(false)}>
          <form className="stack" onSubmit={addNews}>
            <label>
              Title
              <input value={title} onChange={(event) => setTitle(event.target.value)} maxLength={80} required />
            </label>
            <label>
              Body
              <textarea value={body} onChange={(event) => setBody(event.target.value)} maxLength={600} required />
            </label>
            <label>
              Image URL
              <input value={image} onChange={(event) => setImage(event.target.value)} placeholder="Optional" />
            </label>
            {newsError ? <p className="form-error">{newsError}</p> : null}
            <button type="submit" className="btn primary">
              Post
            </button>
          </form>
        </Modal>
      ) : null}
    </div>
  );
}

function Stat({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div className="stat">
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}
