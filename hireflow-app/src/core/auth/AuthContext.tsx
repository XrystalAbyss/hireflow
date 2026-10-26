import React, { createContext, useContext, useEffect, useState, useCallback } from 'react'
import type { LoginRequestDto, UserProfileDto } from '@/types/api'
import { apiClient } from '@/core/api/client'
import { useQueryClient } from '@tanstack/react-query'

interface AuthContextType {
  user: UserProfileDto | null
  isLoading: boolean
  isAuthenticated: boolean
  login: (credentials: LoginRequestDto) => Promise<UserProfileDto>
  logout: () => Promise<void>
  refreshUser: () => Promise<void>
}

const AuthContext = createContext<AuthContextType | undefined>(undefined)

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<UserProfileDto | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const queryClient = useQueryClient()

  const refreshUser = useCallback(async () => {
    try {
      const profile = await apiClient.getCurrentUser()
      setUser(profile)
    } catch {
      setUser(null)
    } finally {
      setIsLoading(false)
    }
  }, [])

  useEffect(() => {
    // Configure 401 handler
    apiClient.setOnUnauthorized(() => {
      setUser((currentUser) => {
        if (currentUser !== null) {
          queryClient.clear()
        }
        return null
      })
    })

    // Check initial session
    refreshUser()
  }, [refreshUser, queryClient])

  const login = async (credentials: LoginRequestDto): Promise<UserProfileDto> => {
    setIsLoading(true)
    try {
      // Clear query cache to avoid cross-session data leakage
      queryClient.clear()
      const profile = await apiClient.login(credentials)
      setUser(profile)
      return profile
    } finally {
      setIsLoading(false)
    }
  }

  const logout = async (): Promise<void> => {
    try {
      await apiClient.logout()
    } catch {
      // Ignore network errors on logout
    } finally {
      setUser(null)
      queryClient.clear()
    }
  }

  return (
    <AuthContext.Provider
      value={{
        user,
        isLoading,
        isAuthenticated: !!user,
        login,
        logout,
        refreshUser,
      }}
    >
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth(): AuthContextType {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider')
  }
  return context
}
