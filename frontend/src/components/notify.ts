import { notifications } from '@mantine/notifications';
import { ApiError } from '../api/http';

export function notifySuccess(message: string): void {
  notifications.show({ color: 'green', message });
}

/** Shows an API error; field errors are shown on the form instead, so they only get a short summary here. */
export function notifyError(error: unknown): void {
  if (error instanceof ApiError) {
    notifications.show({ color: 'red', title: error.message, message: error.code });
  } else {
    notifications.show({ color: 'red', title: 'Something went wrong', message: String(error) });
  }
}
