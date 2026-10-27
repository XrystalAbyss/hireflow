import { afterEach, describe, expect, it, vi } from 'vitest'
import { QueryClient } from '@tanstack/react-query'
import * as studentApi from '@/features/student/api/studentApi'
import { warmRoute } from '@/core/navigation/warmRoute'

afterEach(() => vi.restoreAllMocks())

describe('navigation cache', () => {
  it('reuses fresh destination data and fetches again after the session cache is cleared', async () => {
    const fetchDrives = vi.spyOn(studentApi, 'fetchStudentDrives').mockResolvedValue([])
    const queryClient = new QueryClient({ defaultOptions: { queries: { staleTime: 20_000, retry: false } } })

    await warmRoute('/student/drives', queryClient)
    await warmRoute('/student/drives', queryClient)
    expect(fetchDrives).toHaveBeenCalledTimes(1)
    expect(queryClient.getQueryData(studentApi.studentQueryKeys.drives())).toEqual([])

    queryClient.clear()
    await warmRoute('/student/drives', queryClient)
    expect(fetchDrives).toHaveBeenCalledTimes(2)
  })
})
