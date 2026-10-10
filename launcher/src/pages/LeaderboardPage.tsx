import { useEffect, useState } from 'react';
import { api } from '../api';
import { playClick } from '../audio';
import { formatNum } from '../format';
import { useSession } from '../session';
import type { LeaderRow } from '../types';

export function LeaderboardPage() {
  const { toast, user } = useSession();
  const [rows, setRows] = useState<LeaderRow[]>([]);
  const [by, setBy] = useState<'wins' | 'elims' | 'points'>('wins');

  useEffect(() => {
    api<{ rows: LeaderRow[] }>(`/leaderboard?by=${by}`)
      .then((data) => setRows(data.rows))
      .catch((err: Error) => toast(err.message));
  }, [by, toast, user?.displayName, user?.equipped.skin?.id]);

  return (
    <div className="page board">
      <header className="page-head">
        <div>
          <h1>Leaderboards</h1>
          <p>Ranked with the players on this machine.</p>
        </div>
        <div className="segmented" role="tablist">
          <button type="button" className={by === 'wins' ? 'on' : ''} onClick={() => { playClick(); setBy('wins'); }}>
            Wins
          </button>
          <button type="button" className={by === 'elims' ? 'on' : ''} onClick={() => { playClick(); setBy('elims'); }}>
            Elims
          </button>
          <button type="button" className={by === 'points' ? 'on' : ''} onClick={() => { playClick(); setBy('points'); }}>
            Points
          </button>
        </div>
      </header>
      <ol className="ranks">
        <li className="rank head">
          <span>#</span>
          <span>Player</span>
          <span>{by === 'wins' ? 'Wins' : by === 'elims' ? 'Elims' : 'Points'}</span>
        </li>
        {rows.map((row, index) => (
          <li key={row.id} className={row.you ? 'rank you' : 'rank'}>
            <span className={`place place-${index + 1}`}>{index + 1}</span>
            <span className="player">
              <img className="rank-avatar" src={row.avatar} alt="" draggable={false} />
              {row.name}
              {row.you ? <em>You</em> : null}
            </span>
            <span>{formatNum(row[by])}</span>
          </li>
        ))}
      </ol>
    </div>
  );
}
