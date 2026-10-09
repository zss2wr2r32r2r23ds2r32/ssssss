import { useEffect, useState } from 'react';
import { api } from '../api';
import { playClick } from '../audio';
import { IconCloud, IconFolder } from '../components/Icons';
import { Modal } from '../components/Modal';
import { useSession } from '../session';
import type { Build } from '../types';

interface CatalogSeason {
  name: string;
  version: string;
}

export function LibraryPage() {
  const { toast } = useSession();
  const [builds, setBuilds] = useState<Build[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [mode, setMode] = useState<'add' | 'download' | null>(null);
  const [seasons, setSeasons] = useState<CatalogSeason[]>([]);
  const [name, setName] = useState('');
  const [version, setVersion] = useState('');
  const [folderPath, setFolderPath] = useState('');
  const [formError, setFormError] = useState('');

  async function load() {
    const data = await api<{ builds: Build[]; selectedId: string | null }>('/builds');
    setBuilds(data.builds);
    setSelectedId(data.selectedId);
  }

  useEffect(() => {
    load().catch((err: Error) => toast(err.message));
  }, [toast]);

  async function openDownload() {
    playClick();
    setMode('download');
    try {
      const data = await api<{ seasons: CatalogSeason[] }>('/builds/catalog');
      setSeasons(data.seasons);
    } catch (err) {
      toast(err instanceof Error ? err.message : 'Could not load the catalog.');
    }
  }

  async function addCatalog(season: CatalogSeason) {
    playClick();
    try {
      await api('/builds', { method: 'POST', body: { source: 'catalog', name: season.name } });
      setMode(null);
      await load();
      toast(`Added ${season.name}`);
    } catch (err) {
      toast(err instanceof Error ? err.message : 'Could not add that build.');
    }
  }

  async function detect() {
    playClick();
    setFormError('');
    try {
      const found = await api<{ exists: boolean; version: string | null }>('/builds/detect', {
        method: 'POST',
        body: { folderPath },
      });
      if (!found.exists) {
        setFormError('No folder at that path.');
        return;
      }
      if (found.version) setVersion(found.version);
      else setFormError('Nothing in that folder looked like a version. Type one.');
    } catch (err) {
      setFormError(err instanceof Error ? err.message : 'Could not read that folder.');
    }
  }

  async function addLocal(event: React.FormEvent) {
    event.preventDefault();
    playClick();
    setFormError('');
    try {
      await api('/builds', {
        method: 'POST',
        body: { source: 'local', name, version, folderPath },
      });
      setMode(null);
      setName('');
      setVersion('');
      setFolderPath('');
      await load();
      toast('Build added');
    } catch (err) {
      setFormError(err instanceof Error ? err.message : 'Could not add that build.');
    }
  }

  async function select(id: string) {
    playClick();
    await api('/builds/select', { method: 'POST', body: { id } });
    setSelectedId(id);
    toast('Launch build updated');
  }

  async function remove(id: string) {
    playClick();
    const data = await api<{ builds: Build[]; selectedId: string | null }>(`/builds/${id}`, { method: 'DELETE' });
    setBuilds(data.builds);
    setSelectedId(data.selectedId);
  }

  const count = builds.length === 1 ? '1 build installed' : `${builds.length} builds installed`;

  return (
    <div className="page library">
      <header className="page-head">
        <div>
          <h1>Library</h1>
          <p>{count}</p>
        </div>
        <div className="head-actions">
          <button type="button" className="btn ghost" onClick={openDownload}>
            Download build
          </button>
          <button
            type="button"
            className="btn primary"
            onClick={() => {
              playClick();
              setFormError('');
              setMode('add');
            }}
          >
            + Add build
          </button>
        </div>
      </header>

      {builds.length === 0 ? (
        <div className="empty-grid">
          <button type="button" className="empty-card" onClick={() => { playClick(); setMode('add'); }}>
            <IconFolder />
            <h2>Add an existing install</h2>
            <p>Point Nexa at a folder that already holds a build and it detects the version.</p>
          </button>
          <button type="button" className="empty-card" onClick={openDownload}>
            <IconCloud />
            <h2>Download a build</h2>
            <p>Grab a supported build, then launch it from here.</p>
          </button>
        </div>
      ) : (
        <ul className="build-list">
          {builds.map((build) => (
            <li key={build.id} className={build.id === selectedId ? 'build selected' : 'build'}>
              <button type="button" className="build-main" onClick={() => select(build.id)}>
                <strong>{build.name}</strong>
                <span>
                  {build.version}
                  {build.source === 'catalog' ? ' · recorded locally, no files downloaded' : ''}
                  {build.folderPath ? ` · ${build.folderPath}` : ''}
                </span>
                {build.executablePath ? <em>Executable ready</em> : <em>No executable in this record</em>}
              </button>
              <div className="build-side">
                {build.id === selectedId ? <span className="pill">Selected</span> : <span className="pill quiet-pill">Select</span>}
                <button type="button" className="text-btn" onClick={() => remove(build.id)}>
                  Remove
                </button>
              </div>
            </li>
          ))}
        </ul>
      )}

      {mode === 'add' ? (
        <Modal title="Add an existing install" onClose={() => setMode(null)}>
          <form className="stack" onSubmit={addLocal}>
            <label>
              Name
              <input value={name} onChange={(event) => setName(event.target.value)} placeholder="My install" maxLength={48} />
            </label>
            <label>
              Folder path
              <input
                value={folderPath}
                onChange={(event) => setFolderPath(event.target.value)}
                placeholder="/path/to/build"
                required
              />
            </label>
            <label>
              Version
              <span className="inline-field">
                <input
                  value={version}
                  onChange={(event) => setVersion(event.target.value)}
                  placeholder="Detected from the folder, or type a label"
                />
                <button type="button" className="btn ghost" onClick={detect}>
                  Detect
                </button>
              </span>
            </label>
            {formError ? <p className="form-error">{formError}</p> : null}
            <button type="submit" className="btn primary">
              Add build
            </button>
          </form>
        </Modal>
      ) : null}

      {mode === 'download' ? (
        <Modal title="Download a build" onClose={() => setMode(null)}>
          <p className="lede">These are season labels saved on this machine. Nexa does not download game files.</p>
          <ul className="season-list">
            {seasons.map((season) => (
              <li key={season.name}>
                <div>
                  <strong>{season.name}</strong>
                  <span>{season.version}</span>
                </div>
                <button type="button" className="btn primary" onClick={() => addCatalog(season)}>
                  Add
                </button>
              </li>
            ))}
          </ul>
        </Modal>
      ) : null}
    </div>
  );
}
