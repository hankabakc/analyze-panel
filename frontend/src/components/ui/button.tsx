import * as React from "react"
import { cn } from "@/lib/utils"

export interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'default' | 'destructive' | 'outline' | 'secondary' | 'ghost' | 'link';
  size?: 'default' | 'sm' | 'lg' | 'icon';
}

const Button = React.forwardRef<HTMLButtonElement, ButtonProps>(
  ({ className, variant = 'default', size = 'default', ...props }, ref) => {
    const baseStyles = "inline-flex items-center justify-center whitespace-nowrap rounded-2xl text-[11px] font-black uppercase tracking-[0.2em] ring-offset-background transition-all focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-cyan-500 focus-visible:ring-offset-2 focus-visible:shadow-none disabled:pointer-events-none disabled:opacity-50 active:scale-95 active:translate-y-0.5 hover:-translate-y-0.5";
    
    const variants = {
      default: "bg-slate-900 text-white hover:bg-slate-800 shadow-[0_20px_40px_-10px_rgba(0,0,0,0.2)]",
      destructive: "bg-red-500 text-white hover:bg-red-600 shadow-lg shadow-red-200",
      outline: "border-2 border-slate-100 bg-white hover:bg-slate-50 hover:border-slate-200 text-slate-600",
      secondary: "bg-cyan-500 text-white hover:bg-cyan-600 shadow-[0_20px_40px_-10px_rgba(6,182,212,0.3)]",
      ghost: "hover:bg-slate-100 text-slate-600",
      link: "text-primary underline-offset-4 hover:underline"
    };

    const sizes = {
      default: "h-14 px-8 py-2",
      sm: "h-10 rounded-xl px-4",
      lg: "h-20 rounded-[2rem] px-12 text-sm",
      icon: "h-12 w-12"
    };

    return (
      <button
        className={cn(baseStyles, variants[variant], sizes[size], className)}
        ref={ref}
        {...props}
      />
    )
  }
)
Button.displayName = "Button"

export { Button }
