import React, { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { AuthenticationLayout } from '@/components/shells/AuthenticationLayout'
import { Input } from '@/components/ui/input'
import { PasswordField } from '@/components/ui/password-field'
import { Button } from '@/components/ui/button'
import { FormField } from '@/components/ui/form-field'
import { NoticeBanner } from '@/components/domain/notice-banner'
import { useAuth } from '@/core/auth/AuthContext'
import { ArrowRight } from 'lucide-react'

export const LoginPage: React.FC = () => {
  const { login } = useAuth()
  const navigate = useNavigate()

  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError(null)

    if (!username.trim() || !password) {
      setError('Please provide both institutional username and password.')
      return
    }

    setIsSubmitting(true)
    try {
      const user = await login({ username: username.trim(), password })
      if (user.role === 'STUDENT') {
        navigate('/student')
      } else {
        navigate('/officer')
      }
    } catch (err: unknown) {
      if (err instanceof Error) {
        setError(err.message)
      } else {
        setError('Authentication failed. Please verify credentials with the Placement Office.')
      }
    } finally {
      setIsSubmitting(false)
    }
  }
  return (
    <AuthenticationLayout
      title="Institutional Sign In"
      subtitle="Enter your university-issued credentials to access placement services and recruitment drives."
    >
      <form onSubmit={handleSubmit} noValidate className="space-y-4">
        {error && (
          <NoticeBanner
            variant="error"
            title="Authentication Error"
          >
            {error}
          </NoticeBanner>
        )}

        <FormField
          label="Institutional Username / Roll No."
          id="username"
          required
        >
          <Input
            id="username"
            type="text"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            placeholder="e.g. 2022CS0144 or officer.patel"
            autoComplete="username"
            disabled={isSubmitting}
          />
        </FormField>

        <FormField
          label="Account Password"
          id="password"
          required
        >
          <PasswordField
            id="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            placeholder="••••••••••••"
            autoComplete="current-password"
            disabled={isSubmitting}
          />
        </FormField>

        <div className="pt-2">
          <Button
            type="submit"
            className="w-full justify-center"
            disabled={isSubmitting}
          >
            {isSubmitting ? 'Authenticating Principal...' : 'Sign In to Portal'}
            <ArrowRight className="ml-2 h-4 w-4" />
          </Button>
        </div>
      </form>
    </AuthenticationLayout>
  )
}


