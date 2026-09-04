import { Button, Card, Empty, Input, Modal, Skeleton, Tag, Toast, Typography } from '@douyinfe/semi-ui'
import { IconCopy, IconSearch, IconInfoCircle } from '@douyinfe/semi-icons'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'

import '../../i18n'
import { getModelCatalog, type ModelCatalogItem } from '../../api/portal'

const { Title, Text } = Typography

interface ModelDetailModalProps {
  model: ModelCatalogItem | null
  visible: boolean
  onClose: () => void
}

function ModelDetailModal({ model, visible, onClose }: ModelDetailModalProps) {
  const { t } = useTranslation()
  const [copied, setCopied] = useState(false)

  if (!model) return null

  const handleCopyName = () => {
    void navigator.clipboard.writeText(model.name)
    setCopied(true)
    Toast.success(t('models.nameCopied'))
    setTimeout(() => setCopied(false), 2000)
  }

  const initial = model.name.charAt(0).toUpperCase()

  return (
    <Modal
      visible={visible}
      onCancel={onClose}
      footer={null}
      width={480}
      className="model-detail-modal"
      closable={false}
    >
      {/* Header */}
      <div className="model-detail-header">
        <div className="model-detail-icon">
          <span>{initial}</span>
        </div>
        <div className="model-detail-title">
          <h2 className="model-detail-name">{model.name}</h2>
          <div className="model-detail-meta">
            <span className="model-detail-vendor">{model.vendor}</span>
            <span className="model-detail-sep">·</span>
            <div className="model-detail-groups">
              {model.groups.map((group) => (
                <span key={group} className="model-detail-group-tag">{group}</span>
              ))}
            </div>
          </div>
        </div>
        <button className="model-detail-close" onClick={onClose} aria-label="Close">
          <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
            <path d="M12 4L4 12M4 4l8 8" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
          </svg>
        </button>
      </div>

      {/* Pricing Section */}
      <div className="model-detail-section">
        <h3 className="model-detail-section-title">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <path d="M12 2v20M17 5H9.5a3.5 3.5 0 000 7h5a3.5 3.5 0 010 7H6" />
          </svg>
          {t('models.pricing')}
        </h3>
        {model.priceAvailable ? (
          <div className="model-detail-pricing">
            <div className="model-detail-price-card">
              <span className="model-detail-price-label">{t('models.input')}</span>
              <span className="model-detail-price-value">${model.inputPrice}</span>
              <span className="model-detail-price-unit">/1M</span>
            </div>
            <div className="model-detail-price-card">
              <span className="model-detail-price-label">{t('models.output')}</span>
              <span className="model-detail-price-value">${model.outputPrice}</span>
              <span className="model-detail-price-unit">/1M</span>
            </div>
            {model.cachePrice !== null && (
              <div className="model-detail-price-card model-detail-price-card--secondary">
                <span className="model-detail-price-label">{t('models.cache')}</span>
                <span className="model-detail-price-value">${model.cachePrice}</span>
                <span className="model-detail-price-unit">/1M</span>
              </div>
            )}
          </div>
        ) : (
          <div className="model-detail-empty-pricing">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5">
              <circle cx="12" cy="12" r="10" />
              <path d="M12 8v4M12 16h.01" />
            </svg>
            <span>{t('models.priceUnavailable')}</span>
          </div>
        )}
      </div>

      {/* Info Section */}
      <div className="model-detail-section">
        <h3 className="model-detail-section-title">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <circle cx="12" cy="12" r="10" />
            <path d="M12 16v-4M12 8h.01" />
          </svg>
          {t('models.groups')}
        </h3>
        <div className="model-detail-info-grid">
          <div className="model-detail-info-item">
            <span className="model-detail-info-label">{t('models.vendor')}</span>
            <span className="model-detail-info-value">{model.vendor}</span>
          </div>
          <div className="model-detail-info-item">
            <span className="model-detail-info-label">{t('models.groups')}</span>
            <div className="model-detail-info-tags">
              {model.groups.map((group) => (
                <span key={group} className="model-detail-tag">{group}</span>
              ))}
            </div>
          </div>
        </div>
      </div>

      {/* Actions */}
      <div className="model-detail-actions">
        <button className="model-detail-copy-btn" onClick={handleCopyName}>
          {copied ? (
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M20 6L9 17l-5-5" />
            </svg>
          ) : (
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <rect x="9" y="9" width="13" height="13" rx="2" ry="2" />
              <path d="M5 15H4a2 2 0 01-2-2V4a2 2 0 012-2h9a2 2 0 012 2v1" />
            </svg>
          )}
          <span>{copied ? t('models.copied') : t('models.copyName')}</span>
        </button>
      </div>
    </Modal>
  )
}

export function ModelsPage() {
  const { t } = useTranslation()
  const [models, setModels] = useState<ModelCatalogItem[] | null>(null)
  const [query, setQuery] = useState('')
  const [selectedGroup, setSelectedGroup] = useState('all')
  const [failed, setFailed] = useState(false)
  const [selectedModel, setSelectedModel] = useState<ModelCatalogItem | null>(null)
  const [detailVisible, setDetailVisible] = useState(false)

  useEffect(() => {
    let active = true
    void getModelCatalog().then((items) => { if (active) setModels(items) }).catch(() => { if (active) setFailed(true) })
    return () => { active = false }
  }, [])

  const handleCopyName = useCallback((name: string) => {
    void navigator.clipboard.writeText(name)
    Toast.success(t('models.nameCopied'))
  }, [t])

  const handleShowDetail = useCallback((model: ModelCatalogItem) => {
    setSelectedModel(model)
    setDetailVisible(true)
  }, [])

  const handleCloseDetail = useCallback(() => {
    setDetailVisible(false)
    setSelectedModel(null)
  }, [])

  const groups = useMemo(() => {
    if (!models) return []
    const counts = new Map<string, number>()
    models.forEach((model) => model.groups.forEach((group) => counts.set(group, (counts.get(group) ?? 0) + 1)))
    return Array.from(counts.entries()).sort(([a], [b]) => a.localeCompare(b)).map(([name, count]) => ({ name, count }))
  }, [models])
  const filtered = useMemo(() => (models ?? []).filter((item) =>
    (selectedGroup === 'all' || item.groups.includes(selectedGroup)) && item.name.toLowerCase().includes(query.toLowerCase()),
  ), [models, query, selectedGroup])

  if (failed) return <main className="models-page"><Empty description={t('models.error')} /></main>
  if (!models) return <main className="models-page"><Skeleton active placeholder={<Skeleton.Paragraph rows={12} />} /></main>

  return (
    <main className="models-page">
      <section className="models-hero">
        <div className="models-hero-copy" data-testid="models-hero-copy">
          <Tag color="blue">{t('models.badge')}</Tag>
          <Title heading={1}>{t('models.title')}</Title>
          <Text type="tertiary">{t('models.copy', { count: models.length })}</Text>
        </div>
        <aside className="models-summary" data-testid="models-summary" aria-label={t('models.summaryLabel')}>
          <span className="models-summary-kicker">{t('models.liveCatalog')}</span>
          <strong>{models.length.toString().padStart(2, '0')}</strong>
          <span className="models-summary-label">{t('models.modelsAvailable')}</span>
          <div className="models-summary-line"><span>{t('models.groupsIndexed')}</span><b>{groups.length}</b></div>
        </aside>
      </section>
      <div className="models-layout">
        <nav className="models-groups" aria-label={t('models.groupsLabel')}>
          <button type="button" className={`models-group ${selectedGroup === 'all' ? 'is-selected' : ''}`} aria-pressed={selectedGroup === 'all'} onClick={() => setSelectedGroup('all')}>
            <span>{t('models.allGroups')}</span><b>{models.length}</b>
          </button>
          {groups.map(({ name, count }) => <button key={name} type="button" className={`models-group ${selectedGroup === name ? 'is-selected' : ''}`} aria-pressed={selectedGroup === name} onClick={() => setSelectedGroup(name)}>
            <span>{name}</span><b>{count}</b>
          </button>)}
        </nav>
        <section className="models-results">
          <div className="models-toolbar">
            <span className="models-toolbar-label">{t('models.catalogLabel')}</span>
            <Input prefix={<IconSearch />} placeholder={t('models.search')} value={query} onChange={setQuery} showClear />
            <Text type="tertiary">{t('models.count', { count: filtered.length })}</Text>
          </div>
          {filtered.length === 0 ? <Empty description={t('models.empty')} /> : <section className="models-grid">
            {filtered.map((model) => (
              <Card
                key={model.name}
                className="model-card"
                data-testid={`model-card-${model.name}`}
                data-layout="catalog"
                title={
                  <div className="flex items-center gap-2">
                    <span className="font-mono font-bold">{model.name}</span>
                  </div>
                }
                headerExtraContent={
                  <div className="flex items-center gap-1">
                    <Button
                      theme="borderless"
                      icon={<IconInfoCircle />}
                      aria-label={t('models.showDetail')}
                      onClick={() => handleShowDetail(model)}
                    />
                    <Button
                      theme="borderless"
                      icon={<IconCopy />}
                      aria-label={t('models.copyName')}
                      onClick={() => handleCopyName(model.name)}
                    />
                  </div>
                }
              >
                <div className="model-card-content">
                <div className="model-prices" data-testid={`model-card-${model.name}-pricing`}>
                  {model.priceAvailable ? (
                    <>
                      <span>{t('models.input')} <b>${model.inputPrice}</b></span>
                      <span>{t('models.output')} <b>${model.outputPrice}</b></span>
                      {model.cachePrice !== null && <span>{t('models.cache')} <b>${model.cachePrice}</b></span>}
                    </>
                  ) : (
                    <Text type="tertiary">{t('models.priceUnavailable')}</Text>
                  )}
                </div>
                <div className="model-card-footer">
                  <Tag size="small">{model.groups.join(' · ')}</Tag>
                  <Text type="tertiary">{model.vendor}</Text>
                </div>
                </div>
              </Card>
            ))}
          </section>}
        </section>
      </div>
      <ModelDetailModal
        model={selectedModel}
        visible={detailVisible}
        onClose={handleCloseDetail}
      />
    </main>
  )
}
