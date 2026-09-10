import { Button, Empty, Input, Modal, Pagination, Space, Table, Tag, Toast, Tooltip, Typography } from '@douyinfe/semi-ui'
import { IconEdit, IconEyeOpened, IconPlus, IconRefresh, IconDelete, IconCopy, IconCreditCard, IconKey, IconPause, IconPlay, IconTickCircle } from '@douyinfe/semi-icons'
import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'

import '../../i18n'
import { ConsolePageHeader } from '../../components/ConsolePageHeader'
import { MetricCard } from '../../components/MetricCard'
import { RemoteState } from '../../components/RemoteState'
import {
  createToken,
  deleteToken,
  getTokenKey,
  getTokens,
  setTokenEnabled,
  updateToken,
  type TokenPage,
  type TokenSummary,
  type TokenWriteRequest,
} from '../../api/portal'

interface TokenEditor {
  mode: 'create' | 'edit'
  token?: TokenSummary
}

const QUOTA_PER_USD = 500_000

function formatUsdQuota(quota: number): string {
  return `$${(quota / QUOTA_PER_USD).toFixed(2)}`
}

function displayKey(key: string): string {
  return key.startsWith('sk-') ? key : `sk-${key}`
}

function dateFromTimestamp(timestamp: number): string {
  if (timestamp <= 0) return ''
  return new Date(timestamp * 1000).toISOString().slice(0, 10)
}

function initialDraft(token?: TokenSummary): TokenWriteRequest {
  return {
    name: token?.name ?? '',
    unlimited: token?.unlimited ?? false,
    remainingQuota: token?.remainingQuota ?? 0,
    expiredTime: token?.expiredTime ?? -1,
  }
}

/**
 * Clipboard API 在 HTTP 或未授予权限的浏览器中可能不可用；此处回退到原生复制，
 * 让局域网部署与嵌入式浏览器也能复制已展示的 API Key。
 */
async function copyText(value: string): Promise<boolean> {
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(value)
      return true
    }
  } catch {
    // 继续使用兼容性复制方案。
  }

  const textarea = document.createElement('textarea')
  textarea.value = value
  textarea.setAttribute('readonly', '')
  textarea.style.cssText = 'position:fixed;left:-9999px;top:0;opacity:0'
  document.body.appendChild(textarea)
  // 兼容要求复制源获得焦点的嵌入式浏览器，并确保不会只复制到部分 API Key。
  textarea.focus()
  textarea.select()
  textarea.setSelectionRange(0, textarea.value.length)
  try {
    if (typeof document.execCommand !== 'function') return false
    return document.execCommand('copy')
  } catch {
    return false
  } finally {
    document.body.removeChild(textarea)
  }
}

export function TokensPage() {
  const { t } = useTranslation()
  const [tokens, setTokens] = useState<TokenPage | null>(null)
  const [page, setPage] = useState(1)
  const [revision, setRevision] = useState(0)
  const [failed, setFailed] = useState(false)
  const [editor, setEditor] = useState<TokenEditor | null>(null)
  const [draft, setDraft] = useState<TokenWriteRequest>(initialDraft())
  const [saving, setSaving] = useState(false)
  const [revealedKey, setRevealedKey] = useState<string | null>(null)
  const [pendingDelete, setPendingDelete] = useState<TokenSummary | null>(null)

  const refresh = () => setRevision((current) => current + 1)
  const reloadFromFirstPage = () => {
    setPage(1)
    setRevision((current) => current + 1)
  }

  useEffect(() => {
    let active = true
    setFailed(false)
    setTokens(null)
    void getTokens(page, 50).then((data) => {
      if (active) setTokens(data)
    }).catch(() => {
      if (active) setFailed(true)
    })
    return () => { active = false }
  }, [page, revision])

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
      reloadFromFirstPage()
    }).catch(() => {
      Toast.error(t('tokens.actionError'))
    }).finally(() => setSaving(false))
  }

  const changeStatus = (token: TokenSummary) => {
    void setTokenEnabled(token.id, !token.enabled).then(() => {
      Toast.success(t('tokens.updateSuccess'))
      refresh()
    }).catch(() => Toast.error(t('tokens.actionError')))
  }

  const reveal = (token: TokenSummary) => {
    void getTokenKey(token.id).then(({ key }) => setRevealedKey(displayKey(key)))
      .catch(() => Toast.error(t('tokens.actionError')))
  }

  const copyRevealedKey = () => {
    if (!revealedKey) {
      Toast.error(t('tokens.copyError'))
      return
    }
    void copyText(revealedKey).then((copied) => {
      if (copied) Toast.success(t('tokens.copySuccess'))
      else Toast.error(t('tokens.copyError'))
    }).catch(() => Toast.error(t('tokens.copyError')))
  }

  const remove = () => {
    if (!pendingDelete) return
    const token = pendingDelete
    void deleteToken(token.id).then(() => {
      Toast.success(t('tokens.deleteSuccess'))
      setPendingDelete(null)
      reloadFromFirstPage()
    }).catch(() => Toast.error(t('tokens.actionError')))
  }

  if (failed) return <RemoteState kind="error" onRetry={refresh} />
  if (!tokens) return <RemoteState kind="loading" />

  // 仅统计当前页已加载的令牌，避免把分页外未读取的数据误判为活跃或有限额令牌。
  const activeTokenCount = tokens.items.filter((token) => token.enabled).length
  const limitedQuota = tokens.items
    .filter((token) => !token.unlimited)
    .reduce((total, token) => total + token.remainingQuota, 0)

  const columns = [
    { title: t('tokens.name'), dataIndex: 'name' },
    {
      title: t('tokens.status'),
      dataIndex: 'enabled',
      render: (enabled: boolean) => <Tag color={enabled ? 'green' : 'grey'}>{enabled ? t('tokens.active') : t('tokens.inactive')}</Tag>,
    },
    { title: t('tokens.key'), dataIndex: 'maskedKey', render: (value: string) => <Typography.Text code>{displayKey(value)}</Typography.Text> },
    { title: t('tokens.quota'), dataIndex: 'remainingQuota', render: (value: number, token: TokenSummary) => token.unlimited ? t('tokens.unlimited') : formatUsdQuota(value) },
    {
      title: t('tokens.actions'),
      render: (_: unknown, token: TokenSummary) => (
        <Space spacing="tight">
          <Tooltip content={t('tokens.reveal')}><Button theme="borderless" icon={<IconEyeOpened />} aria-label={t('tokens.reveal')} onClick={() => reveal(token)} /></Tooltip>
          <Tooltip content={t('tokens.edit')}><Button theme="borderless" icon={<IconEdit />} aria-label={t('tokens.edit')} onClick={() => openEditor({ mode: 'edit', token })} /></Tooltip>
          <Tooltip content={token.enabled ? t('tokens.disable') : t('tokens.enable')}><Button theme="borderless" icon={token.enabled ? <IconPause /> : <IconPlay />} aria-label={token.enabled ? t('tokens.disable') : t('tokens.enable')} onClick={() => changeStatus(token)} /></Tooltip>
          <Tooltip content={t('tokens.delete')}><Button theme="borderless" type="danger" icon={<IconDelete />} aria-label={t('tokens.delete')} onClick={() => setPendingDelete(token)} /></Tooltip>
        </Space>
      ),
    },
  ]

  return (
    <main>
      <ConsolePageHeader
        title={t('tokens.title')}
        actions={<Space><Button icon={<IconRefresh />} onClick={refresh}>{t('dashboard.refresh')}</Button><Button theme="solid" type="primary" icon={<IconPlus />} onClick={() => openEditor({ mode: 'create' })}>{t('tokens.create')}</Button></Space>}
      />
      <section className="console-summary-grid" aria-label={t('tokens.title')}>
        <MetricCard label={t('tokens.total')} value={tokens.total} icon={<IconKey />} tone="blue" />
        <MetricCard label={t('tokens.activeCount')} value={activeTokenCount} icon={<IconTickCircle />} tone="mint" />
        <MetricCard label={t('tokens.limitedQuota')} value={formatUsdQuota(limitedQuota)} icon={<IconCreditCard />} tone="amber" />
      </section>
      {tokens.items.length === 0
        ? <Empty description={t('tokens.empty')} />
        : <div className="console-table-wrap"><Table columns={columns} dataSource={tokens.items} rowKey="id" pagination={false} /> </div>}
      {tokens.total > tokens.pageSize && <Pagination currentPage={tokens.page} pageSize={tokens.pageSize} total={tokens.total} onPageChange={setPage} />}

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
          {!draft.unlimited && <><label htmlFor="token-quota">{t('tokens.remainingQuota')} ($)</label><Input id="token-quota" type="number" value={String(draft.remainingQuota / QUOTA_PER_USD)} onChange={(value) => setDraft((current) => ({ ...current, remainingQuota: Math.round((Number(value) || 0) * QUOTA_PER_USD) }))} /></>}
          <label className="token-checkbox"><input type="checkbox" checked={draft.expiredTime === -1} onChange={(event) => setDraft((current) => ({ ...current, expiredTime: event.target.checked ? -1 : Math.floor(Date.now() / 1000) }))} />{t('tokens.neverExpires')}</label>
          <label htmlFor="token-expiration">{t('tokens.expiration')}</label>
          <Input id="token-expiration" type="date" disabled={draft.expiredTime === -1} value={dateFromTimestamp(draft.expiredTime)} onChange={(value) => setDraft((current) => ({ ...current, expiredTime: value ? Math.floor(new Date(`${value}T23:59:59`).getTime() / 1000) : current.expiredTime }))} />
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
