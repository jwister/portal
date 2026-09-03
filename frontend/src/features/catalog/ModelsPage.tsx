import { Button, Card, Empty, Input, Skeleton, Tag, Typography } from '@douyinfe/semi-ui'
import { IconCopy, IconSearch } from '@douyinfe/semi-icons'
import { useEffect, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'

import '../../i18n'
import { getModelCatalog, type ModelCatalogItem } from '../../api/portal'

const { Title, Text } = Typography

export function ModelsPage() {
  const { t } = useTranslation()
  const [models, setModels] = useState<ModelCatalogItem[] | null>(null)
  const [query, setQuery] = useState('')
  const [selectedGroup, setSelectedGroup] = useState('all')
  const [failed, setFailed] = useState(false)

  useEffect(() => {
    let active = true
    void getModelCatalog().then((items) => { if (active) setModels(items) }).catch(() => { if (active) setFailed(true) })
    return () => { active = false }
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
      <section className="models-hero"><Tag color="blue">{t('models.badge')}</Tag><Title heading={1}>{t('models.title')}</Title><Text type="tertiary">{t('models.copy', { count: models.length })}</Text></section>
      <div className="models-layout">
        <nav className="models-groups" aria-label="模型分组">
          <button type="button" className={`models-group ${selectedGroup === 'all' ? 'is-selected' : ''}`} aria-pressed={selectedGroup === 'all'} onClick={() => setSelectedGroup('all')}>
            <span>{t('models.allGroups')}</span><b>{models.length}</b>
          </button>
          {groups.map(({ name, count }) => <button key={name} type="button" className={`models-group ${selectedGroup === name ? 'is-selected' : ''}`} aria-pressed={selectedGroup === name} onClick={() => setSelectedGroup(name)}>
            <span>{name}</span><b>{count}</b>
          </button>)}
        </nav>
        <section className="models-results">
          <div className="models-toolbar">
            <Input prefix={<IconSearch />} placeholder={t('models.search')} value={query} onChange={setQuery} showClear />
            <Text type="tertiary">{t('models.count', { count: filtered.length })}</Text>
          </div>
          {filtered.length === 0 ? <Empty description={t('models.empty')} /> : <section className="models-grid">
            {filtered.map((model) => <Card key={model.name} className="model-card" title={model.name} headerExtraContent={<Button theme="borderless" icon={<IconCopy />} aria-label={t('models.copyName')} onClick={() => navigator.clipboard?.writeText(model.name)} />}>
              <div className="model-prices">{model.priceAvailable ? <><span>{t('models.input')} <b>${model.inputPrice}</b></span><span>{t('models.output')} <b>${model.outputPrice}</b></span>{model.cachePrice !== null && <span>{t('models.cache')} <b>${model.cachePrice}</b></span>}</> : <Text type="tertiary">{t('models.priceUnavailable')}</Text>}</div>
              <div className="model-card-footer"><Tag size="small">{model.groups.join(' · ')}</Tag><Text type="tertiary">{model.vendor}</Text></div>
            </Card>)}
          </section>}
        </section>
      </div>
    </main>
  )
}
