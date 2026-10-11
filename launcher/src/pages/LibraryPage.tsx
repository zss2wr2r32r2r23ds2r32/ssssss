import { useEffect, useRef, useState } from 'react';
import { api, assetUrl } from '../api';
import { playClick } from '../audio';
import { IconBin } from '../components/Icons';
import { useSession } from '../session';
import type { Build } from '../types';

function folderFromFile(file: File) {
  const absolute = window.nexa?.filePath?.(file) || '';
  if (!absolute) return '';
  const relative = file.webkitRelativePath?.replace(/\\/g, '/');
  if (!relative) return absolute;
  const abs = absolute.replace(/\\/g, '/');
  const at = abs.toLowerCase().lastIndexOf(relative.toLowerCase());
  if (at <= 0) return absolute;
  const top = relative.split('/')[0];
  return `${abs.slice(0, at)}${top}`;
}

export function LibraryPage() {
  const { toast } = useSession();
  const [builds, setBuilds] = useState<Build[]>([]);
  const [over, setOver] = useState(false);
  const [cancelled, setCancelled] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);

  async function load() {
    const data = await api<{ builds: Build[]; selectedId: string | null }>('/builds');
    setBuilds(data.builds);
  }

  useEffect(() => {
    const input = inputRef.current;
    input?.setAttribute('webkitdirectory', '');
    input?.setAttribute('directory', '');
    const onCancel = () => setCancelled(true);
    input?.addEventListener('cancel', onCancel);
    load().catch((err: Error) => toast(err.message));
    return () => input?.removeEventListener('cancel', onCancel);
  }, [toast]);

  async function openPicker() {
    playClick();
    setCancelled(false);
    if (window.nexa?.pickFolder) {
      const folderPath = await window.nexa.pickFolder();
      if (!folderPath) {
        setCancelled(true);
        return;
      }
      try {
        await api('/builds/import', { method: 'POST', body: { folderPath } });
        await load();
        toast('Build added');
      } catch (err) {
        toast(err instanceof Error ? err.message : 'Could not add that build.');
      }
      return;
    }
    inputRef.current?.click();
  }

  async function importFolder(files: File[]) {
    const folderPath = files.map(folderFromFile).find(Boolean) || '';
    if (!folderPath) {
      toast('Drop the build folder in the Nexa window so the folder path can be read.');
      return;
    }
    try {
      await api('/builds/import', { method: 'POST', body: { folderPath } });
      await load();
      toast('Build added');
    } catch (err) {
      toast(err instanceof Error ? err.message : 'Could not add that build.');
    }
  }

  async function play(build: Build) {
    playClick();
    try {
      const result = await api<{ name: string }>('/builds/launch', { method: 'POST', body: { id: build.id } });
      toast(`Starting ${result.name}…`);
      await load();
    } catch (err) {
      toast(err instanceof Error ? err.message : 'FortniteClient-Win64-Shipping.exe is missing for this build.');
    }
  }

  async function remove(id: string) {
    playClick();
    const data = await api<{ builds: Build[] }>(`/builds/${id}`, { method: 'DELETE' });
    setBuilds(data.builds);
  }

  const count = builds.length === 1 ? '1 build installed' : `${builds.length} builds installed`;

  return (
    <div
      className={builds.length ? 'page library' : 'page library is-empty'}
      onDragEnter={(event) => {
        event.preventDefault();
        setOver(true);
      }}
      onDragOver={(event) => {
        event.preventDefault();
        setOver(true);
      }}
      onDragLeave={() => setOver(false)}
      onDrop={(event) => {
        event.preventDefault();
        setOver(false);
        void importFolder(Array.from(event.dataTransfer.files));
      }}
    >
      {cancelled ? (
        <div className="import-toast" role="status">
          <span>import cancelled</span>
          <button type="button" aria-label="Dismiss" onClick={() => setCancelled(false)}>
            ×
          </button>
        </div>
      ) : null}

      {builds.length ? (
        <>
          <header className="page-head">
            <div>
              <h1>Library</h1>
              <p>{count}</p>
            </div>
          </header>
          <div className={over ? 'build-grid over' : 'build-grid'}>
            {builds.map((build) => (
              <article key={build.id} className="build-card">
                {build.splashPath ? (
                  <img src={assetUrl(`/builds/${build.id}/splash`)} alt="" draggable={false} />
                ) : (
                  <div className="build-fallback" />
                )}
                <button type="button" className="play-hit" aria-label={`Play ${build.name}`} onClick={() => play(build)}>
                  <span className="play-icon" aria-hidden="true">
                    <svg width="54" height="54" viewBox="0 0 54 54">
                      <circle cx="27" cy="27" r="26" fill="rgba(0,0,0,0.45)" stroke="white" strokeWidth="1.5" />
                      <path d="M22 17.5v19l16-9.5z" fill="white" />
                    </svg>
                  </span>
                </button>
                {build.gameVersion && build.changelist ? (
                  <span className="build-version">
                    <strong>Fortnite {build.gameVersion}</strong>
                    <span>{build.gameVersion}-CL-{build.changelist}</span>
                  </span>
                ) : (
                  <span className="build-name">{build.name}</span>
                )}
                <button
                  type="button"
                  className="build-remove"
                  aria-label={`Remove ${build.name}`}
                  onClick={(event) => {
                    event.preventDefault();
                    event.stopPropagation();
                    void remove(build.id);
                  }}
                >
                  <IconBin size={16} />
                </button>
              </article>
            ))}
          </div>
        </>
      ) : (
        <div className="library-empty">
          <h1>No builds..yet</h1>
          <p>Import a local Fortnite folder to add it to your library.</p>
        </div>
      )}

      <button type="button" className="library-add" aria-label="Import a build" onClick={openPicker}>
        <span className="library-plus">+</span>
      </button>
      <input
        ref={inputRef}
        type="file"
        multiple
        hidden
        onChange={(event) => {
          const files = event.target.files ? Array.from(event.target.files) : [];
          event.target.value = '';
          if (!files.length) {
            setCancelled(true);
            return;
          }
          setCancelled(false);
          void importFolder(files);
        }}
      />
    </div>
  );
}
