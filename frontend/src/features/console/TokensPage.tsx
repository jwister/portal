import { Button, Empty, Input, Modal, Space, Table, Tag, Toast, Typography } from '@douyinfe/semi-ui'
import { IconEdit, IconEyeOpened, IconPlus, IconRefresh, IconDelete, IconCopy } from '@douyinfe/semi-icons'
import { useCallback, useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'

import '../../i18n'
import { ConsolePageHeader } from '../../components/ConsolePageHeader'
import { RemoteState } from '../../components/RemoteState'
import {
  createToken,
  deleteToken,
  getTokenKey,
  getTokens,
  setTokenEnabled,
  updateToken,
  type TokenSummary,
  type TokenWriteRequest,
} from '../../api/portal'

interface TokenEditor {
  mode: 'create' | 'edit'
  token?: TokenSummary
}

function initialDraft(token?: TokenSummary): TokenWriteRequest {
  return {
    name: token?.name ?? '',
    unlimited: token?.unlimited ?? false,
    remainingQuota: token?.remainingQuota ?? 0,
    expiredTime: token?.expiredTime ?? -1,
  }
}

export function TokensPage() {
  const { t } = useTranslation()
  const [tokens, setTokens] = useState<TokenSummary[] | null>(null)
  const [failed, setFailed] = useState(false)
  const [editor, setEditor] = useState<TokenEditor | null>(null)
  const [draft, setDraft] = useState<TokenWriteRequest>(initialDraft())
  const [saving, setSaving] = useState(false)
  const [revealedKey, setRevealedKey] = useState<string | null>(null)
  const [pendingDelete, setPendingDelete] = useState<TokenSummary | null>(null)

  const load = useCallback(() => {
    setFailed(false)
    setTokens(null)
    void getTokens().then(setTokens).catch(() => setFailed(true))
  }, [])

  useEffect(() => {
    load()
  }, [load])

  const openEditor = (next: TokenEditor) => {
    setEditor(next)
    setDraft(initialDraft(next.token))
  }

  const save = () => {
    if (!editor || !draft.name.trim()) return
    const payload = { ...draft, name: draft.name.trim() }
    setSaving(true)
    const request = editor.mode === 'create'
      ? createToken(payload)
      : updateToken(editor.token!.id, payload)
    void request.then(() => {
      Toast.success(editor.mode === 'create' ? t('tokens.createSuccess') : t('tokens.updateSuccess'))
      setEditor(null)
      load()
    }).catch(() => {
      Toast.error(t('tokens.actionError'))
    }).finally(() => setSaving(false))
  }

  const changeStatus = (token: TokenSummary) => {
    void setTokenEnabled(token.id, !token.enabled).then(() => {
      Toast.success(t('tokens.updateSuccess'))
      load()
    }).catch(() => Toast.error(t('tokens.actionError')))
  }

  const reveal = (token: TokenSummary) => {
    void getTokenKey(token.id).then(({ key }) => setRevealedKey(key))
      .catch(() => Toast.error(t('tokens.actionError')))
  }

  const copyRevealedKey = () => {
    if (!revealedKey || !navigator.clipboard) {
      Toast.error(t('tokens.copyError'))
      return
    }
    void navigator.clipboard.writeText(revealedKey)
      .then(() => Toast.success(t('tokens.copySuccess')))
      .catch(() => Toast.error(t('tokens.copyError')))
  }

  const remove = () => {
    if (!pendingDelete) return
    const token = pendingDelete
    void deleteToken(token.id).then(() => {
      Toast.success(t('tokens.deleteSuccess'))
      setPendingDelete(null)
      load()
    }).catch(() => Toast.error(t('tokens.actionError')))
  }

  if (failed) return <RemoteState kind="error" onRetry={load} />
  if (!tokens) return <RemoteState kind="loading" />

  const columns = [
    { title: t('tokens.name'), dataIndex: 'name' },
    { title: t('tokens.key'), dataIndex: 'maskedKey', render: (value: string) => <Typography.Text code>{value}</Typography.Text> },
    {
      title: t('tokens.status'),
      dataIndex: 'enabled',
      render: (enabled: boolean) => <Tag color={enabled ? 'green' : 'grey'}>{enabled ? t('tokens.active') : t('tokens.inactive')}</Tag>,
    },
    { title: t('tokens.quota'), dataIndex: 'remainingQuota' },
    { title: t('tokens.usedQuota'), dataIndex: 'usedQuota' },
    {
      title: t('tokens.actions'),
      render: (_: unknown, token: TokenSummary) => (
        <Space spacing="tight">
          <Button theme="borderless" icon={<IconEyeOpened />} aria-label={t('tokens.reveal')} onClick={() => reveal(token)} />
          <Button theme="borderless" icon={<IconEdit />} aria-label={t('tokens.edit')} onClick={() => openEditor({ mode: 'edit', token })} />
          <Button theme="borderless" onClick={() => changeStatus(token)}>{token.enabled ? t('tokens.disable') : t('tokens.enable')}</Button>
          <Button theme="borderless" type="danger" icon={<IconDelete />} aria-label={t('tokens.delete')} onClick={() => setPendingDelete(token)} />
        </Space>
      ),
    },
  ]

  return (
    <main>
      <ConsolePageHeader
        title={t('tokens.title')}
        actions={<Space><Button icon={<IconRefresh />} onClick={load}>{t('dashboard.refresh')}</Button><Button theme="solid" type="primary" icon={<IconPlus />} onClick={() => openEditor({ mode: 'create' })}>{t('tokens.create')}</Button></Space>}
      />
      {tokens.length === 0
        ? <Empty description={t('tokens.empty')} />
        : <div className="console-table-wrap"><Table columns={columns} dataSource={tokens} rowKey="id" pagination={false} /> </div>}

      <Modal
        title={editor?.mode === 'create' ? t('tokens.create') : t('tokens.edit')}
        visible={editor !== null}
        onCancel={() => setEditor(null)}
        footer={<Space><Button onClick={() => setEditor(null)}>{t('tokens.cancel')}</Button><Button theme="solid" type="primary" loading={saving} disabled={!draft.name.trim()} onClick={save}>{editor?.mode === 'create' ? t('tokens.createConfirm') : t('tokens.save')}</Button></Space>}
      >
        <div className="token-editor">
          <label htmlFor="token-name">{t('tokens.nameField')}</label>
          <Input id="token-name" value={draft.name} onChange={(value) => setDraft((current) => ({ ...current, name: value }))} />
          <label className="token-checkbox"><input type="checkbox" checked={draft.unlimited} onChange={(event) => setDraft((current) => ({ ...current, unlimited: event.target.checked }))} />{t('tokens.unlimited')}</label>
          {!draft.unlimited && <><label htmlFor="token-quota">{t('tokens.remainingQuota')}</label><Input id="token-quota" type="number" value={String(draft.remainingQuota)} onChange={(value) => setDraft((current) => ({ ...current, remainingQuota: Number(value) || 0 }))} /></>}
          <label htmlFor="token-expiration">{t('tokens.expiration')}</label>
          <Input id="token-expiration" type="number" value={String(draft.expiredTime)} onChange={(value) => setDraft((current) => ({ ...current, expiredTime: Number(value) || -1 }))} />
          <Typography.Text type="tertiary">{t('tokens.neverExpires')}: -1</Typography.Text>
        </div>
      </Modal>

      <Modal title={t('tokens.revealTitle')} visible={revealedKey !== null} onCancel={() => setRevealedKey(null)} footer={<Space><Button icon={<IconCopy />} onClick={copyRevealedKey}>{t('tokens.copy')}</Button><Button onClick={() => setRevealedKey(null)}>{t('tokens.cancel')}</Button></Space>}>
        <Typography.Paragraph>{t('tokens.revealWarning')}</Typography.Paragraph>
        <Typography.Text code>{revealedKey}</Typography.Text>
      </Modal>

      <Modal title={t('tokens.deleteConfirm')} visible={pendingDelete !== null} onCancel={() => setPendingDelete(null)} footer={<Space><Button onClick={() => setPendingDelete(null)}>{t('tokens.cancel')}</Button><Button theme="solid" type="danger" onClick={remove}>{t('tokens.delete')}</Button></Space>}>
        <Typography.Paragraph>{t('tokens.deleteWarning')}</Typography.Paragraph>
      </Modal>
    </main>
  )
}
