import { useState } from 'react'
import { api } from '../../lib/api'
import { useApp } from '../../store/AppState'
import { Modal } from '../ui/Modal'

export function ProfileBar() {
  const { config, setConfig, pushToast } = useApp()
  const [creating, setCreating] = useState(false)
  const [name, setName] = useState('')
  if (!config) return null

  return (
    <>
      <div className="row" style={{ justifyContent: 'space-between' }}>
        <div className="row">
          <select
            value={config.activeProfileId}
            onChange={async (event) => {
              setConfig(await api.setActiveProfile(event.target.value))
            }}
          >
            {config.profiles.map((profile) => (
              <option key={profile.id} value={profile.id}>
                {profile.name}
                {profile.id === config.defaultProfileId ? ' · default' : ''}
              </option>
            ))}
          </select>
          <button type="button" className="btn ghost" onClick={() => setCreating(true)}>
            New
          </button>
          <button
            type="button"
            className="btn ghost"
            onClick={async () => setConfig(await api.duplicateProfile(config.activeProfileId))}
          >
            Duplicate
          </button>
          <button
            type="button"
            className="btn ghost"
            onClick={async () => setConfig(await api.setDefaultProfile(config.activeProfileId))}
          >
            Set default
          </button>
          <button
            type="button"
            className="btn danger"
            onClick={async () => {
              const result = await api.deleteProfile(config.activeProfileId)
              if (result.ok && result.data) setConfig(result.data)
              pushToast({ tone: result.ok ? 'success' : 'warn', title: 'Profiles', body: result.message })
            }}
          >
            Delete
          </button>
        </div>
      </div>
      {creating ? (
        <Modal
          title="Create profile"
          onClose={() => setCreating(false)}
          actions={
            <>
              <button type="button" className="btn ghost" onClick={() => setCreating(false)}>
                Cancel
              </button>
              <button
                type="button"
                className="btn primary"
                onClick={async () => {
                  if (!name.trim()) return
                  setConfig(await api.createProfile(name.trim()))
                  setCreating(false)
                  setName('')
                }}
              >
                Create
              </button>
            </>
          }
        >
          <div className="field">
            <label>Profile name</label>
            <input value={name} onChange={(event) => setName(event.target.value)} placeholder="Scrims, Ranked, Creative..." />
          </div>
        </Modal>
      ) : null}
    </>
  )
}
