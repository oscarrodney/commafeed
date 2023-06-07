import { ActionIcon, Button, Tooltip, useMantineTheme } from "@mantine/core"
import { ActionIconProps } from "@mantine/core/lib/ActionIcon/ActionIcon"
import { ButtonProps } from "@mantine/core/lib/Button/Button"
import { useMediaQuery } from "@mantine/hooks"
import { forwardRef, MouseEventHandler, ReactNode } from "react"

interface ActionButtonProps {
    className?: string
    icon?: ReactNode
    label?: ReactNode
    onClick?: MouseEventHandler
    variant?: ActionIconProps["variant"] & ButtonProps["variant"]
    showLabelOnMobile?: boolean
}

/**
 * Switches between Button with label (desktop) and ActionIcon (mobile)
 */
export const ActionButton = forwardRef<HTMLButtonElement, ActionButtonProps>((props: ActionButtonProps, ref) => {
    const theme = useMantineTheme()
    const variant = props.variant ?? "subtle"
    const mobile = !useMediaQuery(`(min-width: ${theme.breakpoints.lg})`)
    const iconOnly = !props.showLabelOnMobile && (mobile || !props.label)
    return iconOnly ? (
        <Tooltip label={props.label} openDelay={500}>
            <ActionIcon ref={ref} color={theme.primaryColor} variant={variant} className={props.className} onClick={props.onClick}>
                {props.icon}
            </ActionIcon>
        </Tooltip>
    ) : (
        <Button ref={ref} variant={variant} size="xs" className={props.className} leftIcon={props.icon} onClick={props.onClick}>
            {props.label}
        </Button>
    )
})
ActionButton.displayName = "HeaderButton"
